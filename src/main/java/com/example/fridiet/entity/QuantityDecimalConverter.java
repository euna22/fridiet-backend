package com.example.fridiet.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 기존 JSON 숫자/Java 호출 호환성을 유지하면서 DB에는 DECIMAL로 저장한다. */
@Converter
public class QuantityDecimalConverter implements AttributeConverter<Double, BigDecimal> {
    public BigDecimal convertToDatabaseColumn(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
    public Double convertToEntityAttribute(BigDecimal value) { return value == null ? null : value.doubleValue(); }
}
