package com.example.fridiet.service;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryActivityServiceTest {
    private final InventoryBatchRepository batches = mock(InventoryBatchRepository.class);
    private final InventoryActivityRepository activities = mock(InventoryActivityRepository.class);
    private final FridgeMemberRepository members = mock(FridgeMemberRepository.class);
    private final InventoryActivityService service = new InventoryActivityService(batches, activities, members);
    private InventoryBatch batch() {
        return InventoryBatch.builder().id(4L).fridge(Fridge.builder().id(2L).owner(User.builder().id(1L).build()).build())
                .ingredientName("당근").status("ACTIVE").unitType(UnitType.QUANTITY).unit("g").remainingQuantity(300.0).build();
    }
    @Test void discardKeepsSnapshotAndIgnoresRetry() {
        var batch = batch();
        when(batches.findForUpdate(4L)).thenReturn(Optional.of(batch));
        service.closeBatch(1L, 4L, "DISCARDED");
        service.closeBatch(1L, 4L, "DISCARDED");
        var capture = org.mockito.ArgumentCaptor.forClass(InventoryActivity.class);
        verify(activities).save(capture.capture());
        assertEquals(300.0, capture.getValue().getQuantity());
        assertEquals("DISCARDED", capture.getValue().getEventType());
        assertEquals("DISCARDED", batch.getStatus());
        assertEquals(0.0, batch.getRemainingQuantity());
        verify(batches, never()).delete(any());
    }
    @Test void unauthorizedAndConflictingReasonFail() {
        var batch = batch();
        when(batches.findForUpdate(4L)).thenReturn(Optional.of(batch));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.closeBatch(9L, 4L, "CONSUMED")).getStatusCode().value());
        service.closeBatch(1L, 4L, "CONSUMED");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.closeBatch(1L, 4L, "DISCARDED")).getStatusCode().value());
    }
    @Test void expiredSchedulerCannotOverwriteClosedStock() {
        var batch = batch();
        batch.closeAs("DISCARDED");
        assertFalse(batch.checkAndSetExpired(java.time.LocalDate.now()));
        assertEquals("DISCARDED", batch.getStatus());
    }
    @Test void cancelledMealSnapshotIsRetainedForAudit() {
        var event = InventoryActivity.builder().mealEventId(31L).quantity(40.0).unit("g").build();
        when(activities.findByMealEventIdAndCancelledAtIsNull(31L)).thenReturn(java.util.List.of(event));
        service.cancelMeal(31L);
        assertNotNull(event.getCancelledAt());
        assertEquals(40.0, event.getQuantity());
        verify(activities, never()).delete(any());
    }
}
