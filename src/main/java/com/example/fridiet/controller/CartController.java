package com.example.fridiet.controller;

import com.example.fridiet.dto.BuyRequest;
import com.example.fridiet.dto.CartCreateRequest;
import com.example.fridiet.dto.CartResponse;
import com.example.fridiet.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 새 장바구니 생성
     */
    @PostMapping
    public ResponseEntity<CartResponse> createCart(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CartCreateRequest.CreateCart request) {

        CartResponse response = cartService.createCart(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 특정 냉장고의 ACTIVE 장바구니 품목 조회
     */
    @GetMapping
    public ResponseEntity<List<CartResponse>> getActiveCarts(@AuthenticationPrincipal Long userId, @RequestParam Long fridgeId) {
        List<CartResponse> response = cartService.getActiveCarts(userId, fridgeId);
        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 품목 추가
     */
    @PostMapping("/{cartId}/items")
    public ResponseEntity<CartResponse.CartItemResponse> addItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long cartId,
            @Valid @RequestBody CartCreateRequest.AddItem request) {

        CartResponse.CartItemResponse response = cartService.addItemToCart(userId, cartId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 품목 상태 변경 (NEEDED -> BOUGHT, REMOVED)
     */
    @PatchMapping("/items/{itemId}/state")
    public ResponseEntity<CartResponse.CartItemResponse> updateItemState(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long itemId,
            @Valid @RequestBody CartCreateRequest.UpdateItemState request) {

        CartResponse.CartItemResponse response = cartService.updateItemState(userId, itemId, request.getState());
        return ResponseEntity.ok(response);
    }

    /**
     * "구매 완료" 클릭 시 재고 등록 & 장바구니에서 삭제
     */
    @PostMapping("/items/{itemId}/buy")
    public ResponseEntity<Long> buyItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long itemId,
            @RequestBody(required = false)BuyRequest request) {

        Long inventoryBatchId = cartService.buyAndMoveToInventory(userId, itemId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryBatchId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
