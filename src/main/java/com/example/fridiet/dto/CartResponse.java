package com.example.fridiet.dto;

import com.example.fridiet.entity.Cart;
import com.example.fridiet.entity.CartItem;
import com.example.fridiet.entity.UnitType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CartResponse {

    private Long cartId;
    private Long fridgeId;
    private Long ownerUserId;
    private String name;
    private String status; // ACTIVE, ARCHIVED
    private List<CartItemResponse> items;
    private LocalDateTime createdAt;

    public static CartResponse from(Cart cart, List<CartItem> items) {
        return CartResponse.builder()
                .cartId(cart.getId())
                .fridgeId(cart.getFridge().getId())
                .ownerUserId(cart.getOwner().getId())
                .name(cart.getName())
                .status(cart.getStatus())
                .items(items.stream().map(CartItemResponse::from).toList())
                .createdAt(cart.getCreatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class CartItemResponse {
        private Long itemId;
        private Long ingredientMasterId;
        private String itemName;
        private UnitType unitType;
        private Double quantity;
        private String customQuantityText;
        private String unit;
        private String state; // NEEDED, BOUGHT, REMOVED
        private String memo;
        private String sourceType;
        private Long sourceRefId;
        private Long createdByUserId;
        private Long boughtByUserId;

        public static CartItemResponse from(CartItem item) {
            return CartItemResponse.builder()
                    .itemId(item.getId())
                    .ingredientMasterId(item.getIngredientMaster() != null ? item.getIngredientMaster().getId() : null)
                    .itemName(item.getItemName())
                    .unitType(item.getUnitType())
                    .quantity(item.getQuantity())
                    .customQuantityText(item.getCustomQuantityText())
                    .unit(item.getUnit())
                    .state(item.getState())
                    .memo(item.getMemo())
                    .sourceType(item.getSourceType())
                    .sourceRefId(item.getSourceRefId())
                    .createdByUserId(item.getCreatedBy().getId())
                    .boughtByUserId(item.getBoughtBy() != null ? item.getBoughtBy().getId() : null)
                    .build();
        }
    }
}