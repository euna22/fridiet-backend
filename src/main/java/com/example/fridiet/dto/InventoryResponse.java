package com.example.fridiet.dto;

import com.example.fridiet.entity.Container;
import com.example.fridiet.entity.InventoryBatch;
import com.example.fridiet.entity.UnitType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
@Builder
public class InventoryResponse {

    private Long batchId;
    private Long fridgeId;
    private Long ingredientMasterId;
    private String ingredientName;
    private UnitType unitType;             // QUANTITY, PERCENT, CUSTOM
    private Double remainingQuantity;      // 수량 또는 % (CUSTOM일 땐 null)
    private String customQuantityText;     // CUSTOM용 텍스트
    private String unit;                   // 단위
    private String countBasis;
    private Double perCountQuantity;
    private String perCountUnit;
    private boolean quantityNeedsReview;
    private LocalDate consumeByDate;
    private Long dDay;                     // 숫자(3, 0, -2)
    private String dDayText;               // 문자열(D-3, D-Day, D+2 등)
    private String locationCategory;       // REFRIGERATED, FROZEN, ROOM_TEMP
    private String status;                 // ACTIVE, EXPIRED, CONSUMED
    private String category;
    private Boolean qrLinked;
    private Long containerId;
    private Long createdBy;
    private LocalDateTime createdAt;

    public static InventoryResponse from(InventoryBatch inventoryBatch, Container container) {
        LocalDate today = LocalDate.now();
        LocalDate consumeDate = inventoryBatch.getConsumeByDate();

        Long dDay = null;
        String dDayText = null;

        if(consumeDate != null) {
            dDay = ChronoUnit.DAYS.between(today, consumeDate);

            if(dDay > 0) {
                dDayText = "D-" + dDay;
            } else if(dDay == 0) {
                dDayText = "D-Day";
            } else {
                dDayText = "D+" + Math.abs(dDay);
            }
        }

        return InventoryResponse.builder()
                .batchId(inventoryBatch.getId())
                .fridgeId(inventoryBatch.getFridge().getId())
                .ingredientMasterId(inventoryBatch.getIngredientMaster() != null ? inventoryBatch.getIngredientMaster().getId() : null)
                .ingredientName(inventoryBatch.getIngredientName())
                .unitType(inventoryBatch.getUnitType())
                .remainingQuantity(inventoryBatch.getRemainingQuantity())
                .customQuantityText(inventoryBatch.getCustomQuantityText())
                .unit(inventoryBatch.getUnit())
                .countBasis(inventoryBatch.getCountBasis())
                .perCountQuantity(inventoryBatch.getPerCountQuantity())
                .perCountUnit(inventoryBatch.getPerCountUnit())
                .quantityNeedsReview(inventoryBatch.isQuantityNeedsReview())
                .consumeByDate(inventoryBatch.getConsumeByDate())
                .dDay(dDay)
                .dDayText(dDayText)
                .locationCategory(inventoryBatch.getLocationCategory())
                .status(inventoryBatch.getStatus())
                .category(inventoryBatch.getIngredientMaster() != null ? inventoryBatch.getIngredientMaster().getCategory() : null)
                .qrLinked(container != null)
                .containerId(container != null ? container.getId() : null)
                .createdBy(inventoryBatch.getCreatedBy())
                .createdAt(inventoryBatch.getCreatedAt())
                .build();
    }
}
