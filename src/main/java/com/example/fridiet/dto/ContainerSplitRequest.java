package com.example.fridiet.dto;

import com.example.fridiet.entity.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ContainerSplitRequest {

    @NotBlank(message = "QR 코드 정보는 필수입니다.")
    private String qrCodeToken;

    @NotNull(message = "냉장고 ID는 필수입니다.")
    private Long fridgeId;

    private String containerName;
    private Long sourceBatchId; // 기존 재고 batch Id
    private UnitType containerUnitType;
    private Double transferredQuantity; // 기존 재고에서 덜어낼 양(QUANTITY, PERCENT)
    private Double containerRemainingQuantity; // 새 컨테이너의 초기 잔량(QUANTITY, PERCENT)
    private String transferredText; // 기존 재고에서 덜어낼 양(CUSTOM)
    private String containerRemainingText; // 새 컨테이너의 초기 잔량(CUSTOM)
    private String sourceUnit;
}