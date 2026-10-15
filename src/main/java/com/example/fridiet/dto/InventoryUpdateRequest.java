package com.example.fridiet.dto;

import com.example.fridiet.entity.UnitType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class InventoryUpdateRequest extends QuantityMetadata {

    private UnitType unitType;
    private Double remainingQuantity;
    private String unit;
    private String customQuantityText;
    private LocalDate consumeByDate;
    private String locationCategory;
}
