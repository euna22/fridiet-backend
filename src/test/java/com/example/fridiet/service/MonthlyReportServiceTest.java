package com.example.fridiet.service;

import com.example.fridiet.entity.*;
import com.example.fridiet.repository.RecipeRecommendationFeedbackRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

class MonthlyReportServiceTest {
    private final RecipeRecommendationFeedbackRepository repository = mock(RecipeRecommendationFeedbackRepository.class);
    // UTC Aug 31 is already September in Korea.
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T16:00:00Z"), ZoneOffset.UTC);
    private final com.example.fridiet.repository.InventoryActivityRepository activities = mock(com.example.fridiet.repository.InventoryActivityRepository.class);
    private final MonthlyReportService service = new MonthlyReportService(repository, activities, clock);

    private RecipeRecommendationFeedback event(String key, String name, RecipeFeedbackEventType type, String at) {
        return RecipeRecommendationFeedback.builder().recipeKey(key).recipeName(name).eventType(type)
                .createdAt(LocalDateTime.parse(at)).build();
    }

    @Test void defaultMonthUsesKoreaAndAuthenticatedUser() {
        var result = service.getReport(8L, null);
        assertEquals("2026-09", result.month());
        assertTrue(result.partialMonth());
        assertEquals(0, result.cookedCount());
        assertEquals(1, result.dailyActivity().size());
        verify(repository).findMonthlyActivity(eq(8L), eq(LocalDateTime.parse("2026-09-01T00:00:00")),
                eq(LocalDateTime.parse("2026-09-01T01:00:00")), any());
    }

    @Test void deduplicatesByKeyDayAndExcludesOutsideMonth() {
        when(repository.findMonthlyActivity(anyLong(), any(), any(), any())).thenReturn(List.of(
                event("A:1", "새 이름", RecipeFeedbackEventType.COOKED, "2026-08-31T23:59:59"),
                event("A:1", "옛 이름", RecipeFeedbackEventType.COOKED, "2026-08-31T09:00:00"),
                event("A:1", "옛 이름", RecipeFeedbackEventType.COOKED, "2026-08-01T00:00:00"),
                event("A:2", "새 이름", RecipeFeedbackEventType.COOKED, "2026-08-01T00:00:00"),
                event("A:1", "새 이름", RecipeFeedbackEventType.SAVED, "2026-08-12T00:00:00"),
                event("A:1", "옛 이름", RecipeFeedbackEventType.SAVED, "2026-08-11T00:00:00"),
                event("A:3", "다음 달", RecipeFeedbackEventType.COOKED, "2026-09-01T00:00:00"),
                event("A:4", "이전 달", RecipeFeedbackEventType.COOKED, "2026-07-31T23:59:59"),
                event("A:5", "노출 제외", RecipeFeedbackEventType.SHOWN, "2026-08-10T12:00:00")));
        var result = service.getReport(1L, "2026-08");
        assertEquals(3, result.cookedCount());
        assertEquals(2, result.uniqueCookedRecipes());
        assertEquals(1, result.savedRecipeCount());
        assertEquals(3, result.activeDays());
        assertEquals(31, result.dailyActivity().size());
        assertEquals(2, result.topRecipes().get(0).count());
        assertEquals("새 이름", result.topRecipes().get(0).recipeName());
        assertFalse(result.partialMonth());
    }

    @Test void handlesLegacyNameDuplicatesWithoutMutatingRows() {
        when(repository.findMonthlyActivity(anyLong(), any(), any(), any())).thenReturn(List.of(
                event(null, "두부구이", RecipeFeedbackEventType.COOKED, "2026-08-05T09:00:00"),
                event(null, "두부구이", RecipeFeedbackEventType.COOKED, "2026-08-05T12:00:00")));
        assertEquals(1, service.getReport(1L, "2026-08").cookedCount());
        verify(repository, never()).save(any());
    }

    @Test void rejectsInvalidFutureOrUnauthenticatedRequests() {
        for (String month : List.of("2026-9", "2026-13", "2026-10", "1999-12", "garbage"))
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.getReport(1L, month)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.getReport(null, "2026-08")).getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test void leapYearMonthHas29DailyEntries() {
        assertEquals(29, service.getReport(1L, "2024-02").dailyActivity().size());
    }
    @Test void cancelledConsumptionIsExcludedFromMonthlyTotals() {
        var cancelled = InventoryActivity.builder().ingredientName("당근").eventType("CONSUMED")
                .unitType(UnitType.QUANTITY).unit("g").quantity(40.0).build();
        cancelled.cancel();
        when(activities.findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of(cancelled));
        assertTrue(service.getReport(1L, "2026-08").stockActivity().isEmpty());
    }

    @Test void stockTotalsKeepUnitsSeparateAndDoNotSumPercentages() {
        when(activities.findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(anyLong(), any(), any()))
                .thenReturn(List.of(
                    InventoryActivity.builder().ingredientName("당근").eventType("CONSUMED").unitType(UnitType.QUANTITY).unit("g").quantity(40.0).build(),
                    InventoryActivity.builder().ingredientName("당근").eventType("DISCARDED").unitType(UnitType.QUANTITY).unit("g").quantity(60.0).build(),
                    InventoryActivity.builder().ingredientName("당근").eventType("CONSUMED").unitType(UnitType.QUANTITY).unit("개").quantity(1.0).build(),
                    InventoryActivity.builder().ingredientName("당근").eventType("CONSUMED").unitType(UnitType.PERCENT).unit("%").quantity(30.0).build()));
        var stock = service.getReport(1L, "2026-08").stockActivity();
        assertEquals(3, stock.size());
        var grams = stock.stream().filter(s -> s.unit().equals("g")).findFirst().orElseThrow();
        assertEquals(40, grams.consumedQuantity().intValue());
        assertEquals(60, grams.discardedQuantity().intValue());
        var unknown = stock.stream().filter(s -> s.unit().equals("수량 미집계")).findFirst().orElseThrow();
        assertNull(unknown.consumedQuantity());
        assertEquals(1, unknown.consumedRecords());
    }
}
