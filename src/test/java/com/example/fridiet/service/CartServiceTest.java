package com.example.fridiet.service;

import com.example.fridiet.dto.CartCreateRequest;
import com.example.fridiet.entity.Cart;
import com.example.fridiet.entity.CartItem;
import com.example.fridiet.entity.Fridge;
import com.example.fridiet.entity.User;
import com.example.fridiet.repository.CartItemRepository;
import com.example.fridiet.repository.CartRepository;
import com.example.fridiet.repository.FridgeRepository;
import com.example.fridiet.repository.IngredientMasterRepository;
import com.example.fridiet.repository.InventoryBatchRepository;
import com.example.fridiet.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private FridgeRepository fridgeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IngredientMasterRepository ingredientMasterRepository;
    @Mock
    private InventoryBatchRepository inventoryBatchRepository;
    @Mock
    private InventoryActivityService access;

    @InjectMocks
    private CartService cartService;

    @Test
    void addItemRejectsDuplicateAcrossActiveCarts() {
        Fixture fixture = fixture("무");
        when(cartItemRepository.existsActiveItemByFridgeIdAndItemName(2L, "무"))
                .thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> cartService.addItemToCart(1L, 3L, fixture.request())
        );

        assertEquals("이미 장바구니에 있는 품목입니다.", error.getMessage());
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    void addItemAllowsReplenishingExistingIngredient() {
        Fixture fixture = fixture("무");
        when(fixture.request().getQuantity()).thenReturn(1.0);
        when(cartItemRepository.existsActiveItemByFridgeIdAndItemName(2L, "무"))
                .thenReturn(false);
        when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        cartService.addItemToCart(1L, 3L, fixture.request());
        verify(cartItemRepository).save(any(CartItem.class));
        verify(inventoryBatchRepository, never()).existsActiveIngredientByFridgeIdAndName(any(), any());
    }

    @Test
    void purchaseRetryReturnsSameBatchWithoutDuplicateStock() {
        User buyer = User.builder().id(1L).build();
        Fridge fridge = Fridge.builder().id(2L).owner(buyer).build();
        Cart cart = Cart.builder().id(3L).fridge(fridge).owner(buyer).status("ACTIVE").build();
        CartItem item = CartItem.builder().cart(cart).createdBy(buyer).itemName("당근").quantity(2.0).unit("개").build();
        when(cartItemRepository.findForUpdate(4L)).thenReturn(Optional.of(item));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(inventoryBatchRepository.save(any())).thenReturn(com.example.fridiet.entity.InventoryBatch.builder().id(99L).build());
        assertEquals(99L, cartService.buyAndMoveToInventory(1L,4L,null));
        assertEquals(99L, cartService.buyAndMoveToInventory(1L,4L,null));
        verify(inventoryBatchRepository).save(any());
        assertEquals(1L, item.getBoughtBy().getId());
    }

    @Test
    void unauthorizedPurchaseCannotWriteStock() {
        Fridge fridge = Fridge.builder().id(2L).build();
        CartItem item = CartItem.builder().cart(Cart.builder().fridge(fridge).build()).build();
        when(cartItemRepository.findForUpdate(4L)).thenReturn(Optional.of(item));
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                .when(access).requireAccess(7L,fridge);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> cartService.buyAndMoveToInventory(7L,4L,null));
        verify(inventoryBatchRepository, never()).save(any());
    }

    private Fixture fixture(String itemName) {
        User user = User.builder()
                .id(1L)
                .email("cart-test@example.com")
                .name("장바구니 테스트")
                .isDeleted(false)
                .build();
        Fridge fridge = Fridge.builder()
                .id(2L)
                .name("테스트 냉장고")
                .owner(user)
                .build();
        Cart cart = Cart.builder()
                .id(3L)
                .fridge(fridge)
                .owner(user)
                .name("테스트 장바구니")
                .status("ACTIVE")
                .build();
        CartCreateRequest.AddItem request = org.mockito.Mockito.mock(CartCreateRequest.AddItem.class);
        when(request.getItemName()).thenReturn(itemName);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findById(3L)).thenReturn(Optional.of(cart));
        when(fridgeRepository.findForUpdate(2L)).thenReturn(Optional.of(fridge));
        return new Fixture(request);
    }

    private record Fixture(CartCreateRequest.AddItem request) {
    }
}
