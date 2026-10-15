package com.example.fridiet.service;

import com.example.fridiet.dto.*;
import com.example.fridiet.entity.*;
import com.example.fridiet.entity.Calendar;
import com.example.fridiet.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarMutationTest {
    final CalendarRepository calendars = mock(CalendarRepository.class);
    final MealConsumptionRepository consumptions = mock(MealConsumptionRepository.class);
    final InventoryBatchRepository batches = mock(InventoryBatchRepository.class);
    final FridgeRepository fridges = mock(FridgeRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final InventoryActivityService activities = mock(InventoryActivityService.class);
    final RecipeService recipes = mock(RecipeService.class);
    final CalendarService service = new CalendarService(calendars, consumptions, batches, fridges, users, activities, recipes);
    final User user = User.builder().id(1L).build();
    final Fridge fridge = Fridge.builder().id(10L).owner(user).build();
    final Calendar calendar = Calendar.builder().owner(user).fridge(fridge).title("당근볶음")
            .mealType("LUNCH").eatenAt(LocalDateTime.of(2026, 1, 1, 12, 0)).requestKey("1:meal-test-123").build();
    final InventoryBatch batch = batch(20L, fridge, 60.0);

    CalendarMutationTest() {
        ReflectionTestUtils.setField(calendar, "id", 30L);
        when(users.findForFeedbackUpdate(1L)).thenReturn(Optional.of(user));
        when(calendars.findForUpdate(30L)).thenReturn(Optional.of(calendar));
        when(batches.findForUpdate(20L)).thenReturn(Optional.of(batch));
        when(consumptions.findAllByCalendarId(30L)).thenReturn(List.of(MealConsumption.builder()
                .calendar(calendar).inventoryBatch(batch).unitType(UnitType.QUANTITY).unit("g")
                .quantityUsed(40.0).remainingQuantity(60.0).build()));
    }

    InventoryBatch batch(Long id, Fridge owner, double remaining) {
        return InventoryBatch.builder().id(id).fridge(owner).ingredientName("당근")
                .unitType(UnitType.QUANTITY).unit("g").remainingQuantity(remaining).status("ACTIVE").build();
    }
    MealRecordRequest.MealItemDto item(Long id, double amount, String unit) {
        var item = mock(MealRecordRequest.MealItemDto.class);
        when(item.getBatchId()).thenReturn(id);
        when(item.getUnitType()).thenReturn(UnitType.QUANTITY);
        when(item.getUnit()).thenReturn(unit);
        when(item.getQuantityUsed()).thenReturn(amount);
        return item;
    }
    MealRecordUpdateRequest update(List<MealRecordRequest.MealItemDto> items) {
        var request = mock(MealRecordUpdateRequest.class);
        when(request.getTitle()).thenReturn("수정한 식사");
        when(request.getMealType()).thenReturn("DINNER");
        when(request.getUsedIngredients()).thenReturn(items);
        return request;
    }

    @Test void updateUsesRestoredAvailabilityAndLocksBeforeWriting() {
        service.updateMeal(30L, 1L, update(List.of(item(20L, 80.0, "g"))));
        assertEquals(20.0, batch.getRemainingQuantity());
        assertEquals("DINNER", calendar.getMealType());
        var order = inOrder(users, calendars, batches, consumptions);
        order.verify(users).findForFeedbackUpdate(1L);
        order.verify(calendars).findForUpdate(30L);
        order.verify(batches).findForUpdate(20L);
        order.verify(consumptions).deleteAllByCalendarId(30L);
        verify(consumptions).save(argThat(row -> row.getQuantityUsed() == 80.0 && row.getRemainingQuantity() == 20.0));
    }
    @Test void invalidReplacementDoesNotRestoreOrDeleteAnything() {
        for (var invalid : List.of(item(20L, 101.0, "g"), item(20L, -1.0, "g"),
                item(20L, Double.NaN, "g"), item(20L, 1.0, "개"))) {
            assertThrows(ResponseStatusException.class, () -> service.updateMeal(30L, 1L, update(List.of(invalid))));
            assertEquals(60.0, batch.getRemainingQuantity());
        }
        verify(consumptions, never()).deleteAllByCalendarId(anyLong());
    }
    @Test void duplicateBatchesAreRejectedBeforeStockLocks() {
        var item = item(20L, 10.0, "g");
        assertThrows(ResponseStatusException.class, () -> service.updateMeal(30L, 1L, update(List.of(item, item))));
        verifyNoInteractions(batches);
    }
    @Test void anotherFridgesStockCannotBeUsedOnEdit() {
        var other = batch(21L, Fridge.builder().id(99L).build(), 50.0);
        when(batches.findForUpdate(21L)).thenReturn(Optional.of(other));
        assertThrows(ResponseStatusException.class, () -> service.updateMeal(30L, 1L, update(List.of(item(21L, 10.0, "g")))));
        assertEquals(60.0, batch.getRemainingQuantity());
        assertEquals(50.0, other.getRemainingQuantity());
    }
    @Test void deletingTwiceRestoresOnlyOnceAndKeepsRequestKey() {
        service.deleteMeal(30L, 1L);
        service.deleteMeal(30L, 1L);
        assertEquals(100.0, batch.getRemainingQuantity());
        assertNotNull(calendar.getDeletedAt());
        assertEquals("1:meal-test-123", calendar.getRequestKey());
        verify(calendars, never()).delete(any());
        verify(batches).findForUpdate(20L);
    }
    @Test void oldCookingRetryAfterDeleteCannotDeductAgain() {
        service.deleteMeal(30L, 1L);
        when(calendars.findByOwnerIdAndRequestKey(1L, "1:meal-test-123")).thenReturn(Optional.of(calendar));
        var request = mock(MealRecordRequest.class);
        when(request.getRequestId()).thenReturn("meal-test-123");
        var error = assertThrows(ResponseStatusException.class, () -> service.recordMealAndDeductStock(1L, request));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(100.0, batch.getRemainingQuantity());
        verify(calendars, never()).save(any());
    }
    @Test void changedUnitsBlockUnsafeRefund() {
        ReflectionTestUtils.setField(batch, "unit", "개");
        assertThrows(ResponseStatusException.class, () -> service.deleteMeal(30L, 1L));
        assertEquals(60.0, batch.getRemainingQuantity());
        assertNull(calendar.getDeletedAt());
    }
    @Test void discardedBatchesAreNotResurrected() {
        batch.delete();
        assertThrows(ResponseStatusException.class, () -> service.deleteMeal(30L, 1L));
        assertEquals("DISCARDED", batch.getStatus());
        assertNull(calendar.getDeletedAt());
    }
    @Test void metadataEditPreservesLegacyQuantitiesWithoutStockWrites() {
        ReflectionTestUtils.setField(batch, "unit", "kg");
        service.updateMeal(30L, 1L, update(null));
        assertEquals("수정한 식사", calendar.getTitle());
        verifyNoInteractions(batches);
        verify(consumptions, never()).deleteAllByCalendarId(anyLong());
    }
    @Test void explicitEmptyListCancelsAllUsage() {
        service.updateMeal(30L, 1L, update(List.of()));
        assertEquals(100.0, batch.getRemainingQuantity());
        verify(consumptions).deleteAllByCalendarId(30L);
        verify(consumptions, never()).save(any());
    }
    @Test void linkedHistoryIsCorrectedWhenUsageChanges() {
        calendar.markActivityLinked();
        when(consumptions.findAllByCalendarId(30L)).thenReturn(
                List.of(MealConsumption.builder().calendar(calendar).inventoryBatch(batch).unitType(UnitType.QUANTITY)
                        .unit("g").quantityUsed(40.0).remainingQuantity(60.0).build()), List.of());
        service.updateMeal(30L, 1L, update(List.of()));
        verify(activities).cancelMeal(30L);
        verify(activities, never()).recordMeal(anyLong(), any(), anyDouble(), any(), anyLong());
        verify(recipes).synchronizeMealFeedback(calendar, false);
    }
    @Test void linkedMetadataEditReschedulesSnapshotsWithoutReinterpretingUnits() {
        calendar.markActivityLinked();
        ReflectionTestUtils.setField(batch, "unit", "개");
        service.updateMeal(30L, 1L, update(null));
        verify(activities).rescheduleMeal(30L, calendar.getEatenAt());
        verify(activities, never()).cancelMeal(anyLong());
        verify(recipes).synchronizeMealFeedback(calendar, false);
    }
    @Test void deletionCancelsOnlyLinkedActivityAndFeedback() {
        service.deleteMeal(30L, 1L);
        verify(activities).cancelMeal(30L);
        verify(recipes).synchronizeMealFeedback(calendar, true);
    }
    @Test void unauthorizedOwnerCannotMutateMeal() {
        when(users.findForFeedbackUpdate(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.deleteMeal(30L, 2L));
        verifyNoInteractions(batches);
        assertNull(calendar.getDeletedAt());
    }
}
