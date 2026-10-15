package com.example.fridiet.service;

import com.example.fridiet.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class LegacyConsumptionCompatibilityTest {
    @Test void numericLegacyTypeRequiresVerifiedHistoryLinkAndDoesNotRewriteRawField() {
        var meal = Calendar.builder().build();
        var row = MealConsumption.builder().calendar(meal).unitType(UnitType.QUANTITY).unit("g").quantityUsed(40.0).build();
        ReflectionTestUtils.setField(row, "unitType", UnitType.CUSTOM);
        assertEquals(UnitType.CUSTOM, row.getUnitType());
        meal.markActivityLinked();
        assertEquals(UnitType.QUANTITY, row.getUnitType());
        assertEquals(UnitType.CUSTOM, ReflectionTestUtils.getField(row, "unitType"));
        assertEquals(40.0, row.getQuantityUsed());
    }

    @Test void actualCustomAndUnknownQuantitiesRemainCustom() {
        var meal = Calendar.builder().build();
        meal.markActivityLinked();
        var row = MealConsumption.builder().calendar(meal).unitType(UnitType.CUSTOM)
                .customQuantityText("절반 남음").preCustomQuantityText("한 통").build();
        assertEquals(UnitType.CUSTOM, row.getUnitType());
        assertNull(row.getQuantityUsed());
    }
}
