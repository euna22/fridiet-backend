package com.example.fridiet.dto;

import com.example.fridiet.entity.UnitType;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class OcrIngredientItemRequest extends QuantityMetadata {

    private String ingredientName;
    private UnitType unitType;
    private Double quantity;
    private String unit;
    private LocalDate consumeByDate;
    private String locationCategory;
}
