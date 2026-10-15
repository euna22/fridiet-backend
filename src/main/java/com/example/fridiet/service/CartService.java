package com.example.fridiet.service;

import com.example.fridiet.dto.CartCreateRequest;
import com.example.fridiet.dto.CartResponse;
import com.example.fridiet.dto.BuyRequest;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final FridgeRepository fridgeRepository;
    private final UserRepository userRepository;
    private final IngredientMasterRepository ingredientMasterRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryActivityService access;

    /**
     * 장바구니 생성 (기본 STATUS: ACTIVE)
     */
    @Transactional
    public CartResponse createCart(Long userId, CartCreateRequest.CreateCart request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        Fridge fridge = fridgeRepository.findById(request.getFridgeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다."));

        access.requireAccess(userId, fridge);
        Cart cart = Cart.builder()
                .fridge(fridge)
                .owner(user)
                .name(request.getName())
                .status("ACTIVE")
                .build();

        Cart savedCart = cartRepository.save(cart);
        return CartResponse.from(savedCart, List.of());
    }

    /**
     * 냉장고별 ACTIVE 장바구니 목록 및 품목 조회
     */
    @Transactional(readOnly = true)
    public List<CartResponse> getActiveCarts(Long userId, Long fridgeId) {
        access.requireAccess(userId, fridgeRepository.findById(fridgeId).orElseThrow());
        List<Cart> activeCarts = cartRepository.findAllByFridgeIdAndStatus(fridgeId, "ACTIVE");

        return activeCarts.stream().map(cart -> {
            List<CartItem> items = cartItemRepository.findAllByCartIdAndStateNot(cart.getId(), "REMOVED");
            return CartResponse.from(cart, items);
        }).toList();
    }

    /**
     * 장바구니 품목 추가 (IngredientMaster 자동 매칭)
     */
    @Transactional
    public CartResponse.CartItemResponse addItemToCart(Long userId, Long cartId, CartCreateRequest.AddItem request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장바구니입니다."));

        // 표준 재료 자동 매칭
        String trimmedName = request.getItemName().trim();
        Long fridgeId = cart.getFridge().getId();
        access.requireAccess(userId, cart.getFridge());
        fridgeRepository.findForUpdate(fridgeId).orElseThrow();
        if (!"ACTIVE".equals(cart.getStatus())) throw new IllegalArgumentException("종료된 장바구니입니다.");
        if (cartItemRepository.existsActiveItemByFridgeIdAndItemName(fridgeId, trimmedName)) {
            throw new IllegalArgumentException("이미 장바구니에 있는 품목입니다.");
        }
        IngredientMaster ingredientMaster = ingredientMasterRepository.findByCanonicalName(trimmedName)
                .orElse(null);

        // UnitType 지정(기본값 QUANTITY)
        UnitType unitType = (request.getUnitType() != null) ? request.getUnitType() : UnitType.QUANTITY;
        validateQuantity(unitType, request.getQuantity(), request.getCustomQuantityText());

        CartItem item = CartItem.builder()
                .cart(cart)
                .ingredientMaster(ingredientMaster)
                .itemName(trimmedName)
                .unitType(unitType)
                .quantity(request.getQuantity())
                .customQuantityText(request.getCustomQuantityText())
                .unit(request.getUnit())
                .state("NEEDED")
                .memo(request.getMemo())
                .sourceType(request.getSourceType() != null ? request.getSourceType() : "MANUAL")
                .sourceRefId(request.getSourceRefId())
                .createdBy(user)
                .build();

        CartItem savedItem = cartItemRepository.save(item);
        return CartResponse.CartItemResponse.from(savedItem);
    }

    /**
     * 품목 상태 변경 (NEEDED -> BOUGHT OR REMOVED)
     */
    @Transactional
    public CartResponse.CartItemResponse updateItemState(Long userId, Long itemId, String newState) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        CartItem item = cartItemRepository.findForUpdate(itemId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장바구니 품목입니다."));

        access.requireAccess(userId, item.getCart().getFridge());
        if (!java.util.Set.of("NEEDED", "BOUGHT", "REMOVED").contains(newState)) throw new IllegalArgumentException("잘못된 상태입니다.");
        if (item.getPurchasedBatchId() != null && !"REMOVED".equals(newState)) throw new IllegalArgumentException("재고로 전환된 품목은 되돌릴 수 없습니다.");
        // 상태 변경 및 구매자 세팅
        item.updateState(newState, user);

        return CartResponse.CartItemResponse.from(item);
    }

    /**
     * 구매 완료: 장바구니 품목을 냉장고 재고로 전환, 장바구니에서 제거
     */
    @Transactional
    public Long buyAndMoveToInventory(Long userId, Long itemId, BuyRequest request) {

        // 장바구니 품목 조회
        CartItem cartItem = cartItemRepository.findForUpdate(itemId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장바구니입니다."));

        Cart cart = cartItem.getCart();
        access.requireAccess(userId, cart.getFridge());
        if (cartItem.getPurchasedBatchId() != null) return cartItem.getPurchasedBatchId();
        if ("REMOVED".equals(cartItem.getState()) || !"ACTIVE".equals(cart.getStatus())) throw new IllegalArgumentException("이미 제거되거나 종료된 품목입니다.");
        User buyer = userRepository.findById(userId).orElseThrow();

        // UnitType 결정(기본값은 QUANTITY)
        UnitType unitType = (request != null && request.getUnitType() != null)
                ? request.getUnitType() : (cartItem.getUnitType() != null ? cartItem.getUnitType() : UnitType.QUANTITY);

        // 수량 및 단위 분기 처리
        Double remainingQuantity = null;
        String customQuantityText = null;
        String unit = null;

        if (unitType == UnitType.CUSTOM) {
            customQuantityText = (request != null && request.getCustomQuantityText() != null)
                    ? request.getCustomQuantityText() : cartItem.getCustomQuantityText();
        } else if (unitType == UnitType.PERCENT) {
            remainingQuantity = (request != null && request.getQuantity() != null)
                    ? request.getQuantity() : (cartItem.getQuantity() != null ? cartItem.getQuantity() : 100.0);
            unit = "%";
        } else {
            remainingQuantity = (request != null && request.getQuantity() != null)
                    ? request.getQuantity() : (cartItem.getQuantity() != null ? cartItem.getQuantity() : 1.0);
            unit = (request != null && request.getUnit() != null)
                    ? request.getUnit() : cartItem.getUnit();
        }

        validateQuantity(unitType, remainingQuantity, customQuantityText);
        // 소비기한 설정(기본값은 3일 뒤)
        LocalDate expirationDate = (request != null && request.getExpirationDate() != null)
                ? request.getExpirationDate() : LocalDate.now().plusDays(3);

        // 보관 장소 설정
        String locationCategory = (request != null && request.getLocationCategory() != null)
                ? request.getLocationCategory() : "REFRIGERATED";

        // InventoryBatch 생성 및 저장
        InventoryBatch newBatch = InventoryBatch.builder()
                .fridge(cart.getFridge())
                .ingredientMaster(cartItem.getIngredientMaster())
                .ingredientName(cartItem.getItemName())
                .unitType(unitType)
                .unit(unit)
                .remainingQuantity(remainingQuantity)
                .customQuantityText(customQuantityText)
                .consumeByDate(expirationDate)
                .locationCategory(locationCategory)
                .status("ACTIVE")
                .createdBy(userId)
                .build();

        if(unitType == UnitType.QUANTITY) {
            newBatch.normalizeQuantity(request);
        }

        InventoryBatch savedBatch = inventoryBatchRepository.save(newBatch);

        // 장바구니 항목 처리(REMOVED)
        cartItem.completePurchase(savedBatch.getId(), buyer);

        return savedBatch.getId();
    }

    private static void validateQuantity(UnitType type, Double quantity, String custom) {
        if (type == UnitType.CUSTOM) {
            if (custom == null || custom.isBlank()) throw new IllegalArgumentException("수량 설명이 필요합니다.");
        } else if (quantity != null && (!Double.isFinite(quantity) || quantity <= 0 || (type == UnitType.PERCENT && quantity > 100))) {
            throw new IllegalArgumentException("수량은 양수여야 하며 비율은 100 이하여야 합니다.");
        }
    }
}
