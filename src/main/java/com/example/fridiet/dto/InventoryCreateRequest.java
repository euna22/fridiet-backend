package com.example.fridiet.dto;

import com.example.fridiet.entity.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class InventoryCreateRequest extends QuantityMetadata {

    @NotNull(message = "냉장고 ID는 필수입니다.")
    private Long fridgeId;

    @NotBlank(message = "식재료 이름은 필수입니다.")
    private String ingredientName;

    @NotNull(message = "단위 입력 유형은 필수입니다.")
    private UnitType unitType; // QUANTITY(기본값), PERCENT, CUSTOM

    // 1) QUANTITY 또는 PERCENT인 경우
    private Double remainingQuantity;
    private String unit;

    // 2) CUSTOM인 경우
    private String customQuantityText;

    @NotNull(message = "소비기한은 필수입니다.")
    private LocalDate consumeByDate;

    private String locationCategory; // REFRIGERATED(기본값), FROZEN, ROOM_TEMP
}
