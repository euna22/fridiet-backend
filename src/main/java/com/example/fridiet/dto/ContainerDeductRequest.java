package com.example.fridiet.dto;

import com.example.fridiet.entity.UnitType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ContainerDeductRequest {

    @NotNull(message = "단위 유형은 필수입니다.")
    private UnitType unitType;

    private Double quantityUsed;
    private String customText;
}
