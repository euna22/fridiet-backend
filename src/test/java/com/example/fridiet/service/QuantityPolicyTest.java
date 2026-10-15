package com.example.fridiet.service;
import com.example.fridiet.entity.*;
import com.example.fridiet.dto.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuantityPolicyTest {
    @Test void sharedCases() throws Exception {
        var tree = new tools.jackson.databind.ObjectMapper().readTree(getClass().getResourceAsStream("/quantity_cases.json"));
        for (var row : tree) {
            var result = QuantityPolicy.normalize(row.get("quantity").asDouble(), row.get("unit").asString());
            assertEquals(row.get("expectedQuantity").asDouble(), result.quantity().doubleValue());
            assertEquals(row.get("expectedUnit").asString(), result.unit());
        }
    }
    @Test void packageContentsAndCountsAreSeparate() {
        var metadata = new QuantityMetadata();
        metadata.setPackageCount(2.0); metadata.setPackageQuantity(500.0); metadata.setPackageUnit("g");
        var batch = InventoryBatch.builder().unitType(UnitType.QUANTITY).unit("봉").remainingQuantity(2.0).build();
        batch.normalizeQuantity(metadata);
        assertEquals(1000.0, batch.getRemainingQuantity()); assertEquals("g", batch.getUnit());
        var unknown = InventoryBatch.builder().unitType(UnitType.QUANTITY).unit("봉").remainingQuantity(2.0).build();
        unknown.normalizeQuantity(null);
        assertEquals(2.0, unknown.getRemainingQuantity()); assertEquals("개", unknown.getUnit()); assertEquals("봉 기준", unknown.getCountBasis());
        assertNull(CookingSuggestionService.factor(unknown, "g"));
        assertNull(CookingSuggestionService.factor(unknown, "개"));
    }
    @Test void confirmedCountWeightOnly() {
        var batch = InventoryBatch.builder().unitType(UnitType.QUANTITY).unit("개").remainingQuantity(2.0).build();
        assertNull(CookingSuggestionService.factor(batch, "g"));
        var metadata = new QuantityMetadata(); metadata.setPerCountQuantity(200.0); metadata.setPerCountUnit("g");
        batch.normalizeQuantity(metadata);
        assertEquals(200.0, CookingSuggestionService.factor(batch,"g").doubleValue());
    }
    @Test void decimalSubtractionHasNoFloatingResidue() {
        var batch = InventoryBatch.builder().unitType(UnitType.QUANTITY).unit("g").remainingQuantity(0.3).status("ACTIVE").build();
        batch.consumeQuantity(0.1,null); batch.consumeQuantity(0.2,null);
        assertEquals(0.0,batch.getRemainingQuantity()); assertEquals("CONSUMED",batch.getStatus());
    }
    @Test void decimalRestorationHasNoFloatingResidue() {
        var batch = InventoryBatch.builder().unitType(UnitType.QUANTITY).unit("g").remainingQuantity(0.1).status("ACTIVE").build();
        batch.restoreQuantity(0.2, null);
        assertEquals(0.3, batch.getRemainingQuantity());
        batch.consumeQuantity(0.3, null);
        assertEquals(0.0, batch.getRemainingQuantity());
        assertEquals("CONSUMED", batch.getStatus());
    }
    @Test void restoredExpiredStockDoesNotBecomeFresh() {
        var batch = InventoryBatch.builder().unitType(UnitType.QUANTITY).unit("개").remainingQuantity(0.0)
                .consumeByDate(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(1))
                .status("CONSUMED").build();
        batch.restoreQuantity(0.5, null);
        assertEquals(0.5, batch.getRemainingQuantity());
        assertEquals("EXPIRED", batch.getStatus());
    }
    @Test void restorationDoesNotResurrectDiscardedStock() {
        var batch = InventoryBatch.builder().unitType(UnitType.QUANTITY).unit("g").remainingQuantity(0.1).status("DISCARDED").build();
        batch.restoreQuantity(0.2, null);
        assertEquals("DISCARDED", batch.getStatus());
    }
    @Test void invalidQuantitiesRejected() {
        assertThrows(Exception.class, () -> QuantityPolicy.normalize(Double.NaN,"g"));
        assertThrows(Exception.class, () -> QuantityPolicy.normalize(-1.0,"g"));
        assertThrows(Exception.class, () -> QuantityPolicy.normalize(1.0,"큰술"));
    }
}
