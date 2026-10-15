package com.example.fridiet.dto;

import com.example.fridiet.entity.Calendar;
import com.example.fridiet.entity.MealConsumption;
import com.example.fridiet.entity.UnitType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MealRecordResponse {

    private Long mealEventId;
    private Long fridgeId;
    private Long ownerUserId;
    private String title;
    private String mealType;
    private String note;
    private String imageUrl;
    private LocalDateTime eatenAt;
    private List<ConsumptionItemResponse> consumedItems;

    public static MealRecordResponse from(Calendar calendar, List<MealConsumption> consumptions) {
        return MealRecordResponse.builder()
                .mealEventId(calendar.getId())
                .fridgeId(calendar.getFridge().getId())
                .ownerUserId(calendar.getOwner().getId())
                .title(calendar.getTitle())
                .mealType(calendar.getMealType())
                .imageUrl(calendar.getImageUrl())
                .note(calendar.getNote())
                .eatenAt(calendar.getEatenAt())
                .consumedItems(consumptions.stream().map(ConsumptionItemResponse::from).toList())
                .build();
    }

    @Getter
    @Builder
    public static class ConsumptionItemResponse {
        private Long consumptionId;
        private Long batchId;
        private String ingredientName;
        private Double quantityUsed;
        private String preCustomQuantityText;
        private UnitType unitType;
        private String customQuantityText;
        private String unit;
        private Double remainingQuantity; // 차감 후 남은 재고 수량

        public static ConsumptionItemResponse from(MealConsumption entity) {
            return ConsumptionItemResponse.builder()
                    .consumptionId(entity.getId())
                    .batchId(entity.getInventoryBatch().getId())
                    .ingredientName(entity.getInventoryBatch().getIngredientName())
                    .quantityUsed(entity.getQuantityUsed())
                    .preCustomQuantityText(entity.getPreCustomQuantityText())
                    .unitType(entity.getUnitType())
                    .customQuantityText(entity.getCustomQuantityText())
                    .unit(entity.getUnit())
                    .remainingQuantity(entity.getInventoryBatch().getRemainingQuantity())
                    .build();
        }
    }
}