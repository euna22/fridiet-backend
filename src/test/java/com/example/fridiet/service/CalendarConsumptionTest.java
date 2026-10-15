package com.example.fridiet.service;
import com.example.fridiet.dto.MealRecordRequest;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CalendarConsumptionTest {
    private final CalendarRepository calendars = mock(CalendarRepository.class);
    private final MealConsumptionRepository consumptions = mock(MealConsumptionRepository.class);
    private final InventoryBatchRepository batches = mock(InventoryBatchRepository.class);
    private final FridgeRepository fridges = mock(FridgeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final InventoryActivityService activities = mock(InventoryActivityService.class);
    private final RecipeService recipes = mock(RecipeService.class);
    private final CalendarService service = new CalendarService(calendars, consumptions, batches, fridges, users, activities, recipes);

    private void checkInvalid(Double amount, String unit, Long batchFridge, UnitType type, boolean duplicate) {
        var user = User.builder().id(1L).build();
        var fridge = Fridge.builder().id(1L).owner(user).build();
        var batch = InventoryBatch.builder().id(2L).fridge(Fridge.builder().id(batchFridge).build())
                .status("ACTIVE").unitType(UnitType.QUANTITY).unit("g").remainingQuantity(100.0).build();
        when(users.findForFeedbackUpdate(1L)).thenReturn(Optional.of(user));
        when(fridges.findById(1L)).thenReturn(Optional.of(fridge));
        when(batches.findForUpdate(2L)).thenReturn(Optional.of(batch));
        var request = mock(MealRecordRequest.class);
        var item = mock(MealRecordRequest.MealItemDto.class);
        when(request.getFridgeId()).thenReturn(1L);
        when(request.getUsedIngredients()).thenReturn(duplicate ? List.of(item, item) : List.of(item));
        when(item.getBatchId()).thenReturn(2L);
        when(item.getUnitType()).thenReturn(type);
        when(item.getUnit()).thenReturn(unit);
        when(item.getQuantityUsed()).thenReturn(amount);
        assertThrows(ResponseStatusException.class, () -> service.recordMealAndDeductStock(1L, request));
        assertEquals(100.0, batch.getRemainingQuantity());
        verify(calendars, never()).save(any());
        verify(consumptions, never()).save(any());
    }
    @Test void rejectsTooMuch() { checkInvalid(101.0, "g", 1L, UnitType.QUANTITY, false); }
    @Test void rejectsNegative() { checkInvalid(-2.0, "g", 1L, UnitType.QUANTITY, false); }
    @Test void rejectsNonFinite() { checkInvalid(Double.NaN, "g", 1L, UnitType.QUANTITY, false); }
    @Test void rejectsOtherFridge() { checkInvalid(20.0, "g", 3L, UnitType.QUANTITY, false); }
    @Test void rejectsUnitMismatch() { checkInvalid(20.0, "kg", 1L, UnitType.QUANTITY, false); }
    @Test void rejectsTypeMismatch() { checkInvalid(20.0, "g", 1L, UnitType.PERCENT, false); }
    @Test void rejectsDuplicateBatch() { checkInvalid(20.0, "g", 1L, UnitType.QUANTITY, true); }

    @Test void deductsStockAndRecordsConsumption() {
        var user = User.builder().id(1L).build();
        var fridge = Fridge.builder().id(1L).owner(user).build();
        var batch = InventoryBatch.builder().id(2L).fridge(fridge).ingredientName("당근")
                .status("ACTIVE").unitType(UnitType.QUANTITY).unit("g").remainingQuantity(100.0).build();
        when(users.findForFeedbackUpdate(1L)).thenReturn(Optional.of(user));
        when(fridges.findById(1L)).thenReturn(Optional.of(fridge));
        when(batches.findForUpdate(2L)).thenReturn(Optional.of(batch));
        when(calendars.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(consumptions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = mock(MealRecordRequest.class);
        var item = mock(MealRecordRequest.MealItemDto.class);
        when(request.getFridgeId()).thenReturn(1L);
        when(request.getUsedIngredients()).thenReturn(List.of(item));
        when(item.getBatchId()).thenReturn(2L);
        when(item.getUnitType()).thenReturn(UnitType.QUANTITY);
        when(item.getUnit()).thenReturn("g");
        when(item.getQuantityUsed()).thenReturn(40.0);
        var result = service.recordMealAndDeductStock(1L, request);
        assertEquals(60.0, batch.getRemainingQuantity());
        assertEquals(40.0, result.getConsumedItems().getFirst().getQuantityUsed());
        assertEquals("ACTIVE", batch.getStatus());
        when(item.getQuantityUsed()).thenReturn(60.0);
        service.recordMealAndDeductStock(1L, request);
        assertEquals(0.0, batch.getRemainingQuantity());
        assertEquals("CONSUMED", batch.getStatus());
    }

    @Test void repeatedRequestDoesNotDeductAgain() {
        var user = User.builder().id(1L).build();
        var fridge = Fridge.builder().id(1L).owner(user).build();
        var previous = com.example.fridiet.entity.Calendar.builder().owner(user).fridge(fridge).title("식사").build();
        when(users.findForFeedbackUpdate(1L)).thenReturn(Optional.of(user));
        when(calendars.findByOwnerIdAndRequestKey(1L, "1:cook-test-1234")).thenReturn(Optional.of(previous));
        when(consumptions.findAllByCalendarId(null)).thenReturn(List.of());
        var request = mock(MealRecordRequest.class);
        when(request.getRequestId()).thenReturn("cook-test-1234");
        service.recordMealAndDeductStock(1L, request);
        verifyNoInteractions(batches, activities);
        verify(calendars, never()).save(any());
    }
}
