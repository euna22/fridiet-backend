package com.example.fridiet.dto;

import com.example.fridiet.entity.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class ContainerRegisterRequest extends QuantityMetadata {

    @NotBlank(message = "QR 코드 정보는 필수입니다.")
    private String qrCodeToken;

    @NotNull(message = "냉장고 ID는 필수입니다.")
    private Long fridgeId;

    private String containerName;

    // 용기에 바로 식재료를 담을 경우
    private Long existingBatchId; // 기존 재고 연결
    private String ingredientName; // 신규 재고 등록 시
    private UnitType unitType;
    private Double remainingQuantity;
    private String customQuantityText;
    private String unit;
    private LocalDate consumeByDate;
    private String locationCategory;
}
