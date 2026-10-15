package com.example.fridiet.dto;

import com.example.fridiet.entity.Container;
import com.example.fridiet.entity.InventoryBatch;
import com.example.fridiet.entity.UnitType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ContainerScanResponse {

    private Long containerId;
    private String qrCodeToken;
    private String containerName;
    private Boolean isEmpty;

    private Long batchId;
    private String ingredientName;
    private UnitType unitType;
    private Double remainingQuantity;
    private String customQuantityText;
    private String unit;
    private boolean quantityNeedsReview;
    private String countBasis;
    private LocalDate consumeByDate;

    public static ContainerScanResponse from(Container container) {
        if (container == null) {
            return ContainerScanResponse.builder()
                    .isEmpty(true)
                    .build();
        }

        InventoryBatch batch = container.getCurrentBatch();
        // batch가 없거나, status가 CONSUMED이면 빈 용기(isEmpty = true)
        boolean isEmpty = (batch == null || "CONSUMED".equals(batch.getStatus()));

        var builder = ContainerScanResponse.builder()
                .containerId(container.getId())
                .qrCodeToken(container.getQrCodeToken())
                .containerName(container.getName() != null ? container.getName() : "스마트 용기")
                .isEmpty(isEmpty);

        // 재고가 정상 보관되어 있는 경우만 재고 상세정보 세팅
        if (!isEmpty) {
            builder.batchId(batch.getId())
                    .ingredientName(batch.getIngredientName())
                    .unitType(batch.getUnitType())
                    .remainingQuantity(batch.getRemainingQuantity())
                    .customQuantityText(batch.getCustomQuantityText())
                    .unit(batch.getUnit())
                    .quantityNeedsReview(batch.isQuantityNeedsReview())
                    .countBasis(batch.getCountBasis())
                    .consumeByDate(batch.getConsumeByDate());
        }

        return builder.build();
    }
}
