package com.example.fridiet.service;

import com.example.fridiet.dto.ContainerSplitRequest;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContainerSplitTest {
    final UserRepository users = mock(UserRepository.class);
    final ContainerRepository containers = mock(ContainerRepository.class);
    final InventoryBatchRepository batches = mock(InventoryBatchRepository.class);
    final FridgeRepository fridges = mock(FridgeRepository.class);
    final InventoryActivityService access = mock(InventoryActivityService.class);
    final ContainerService service = new ContainerService(users, containers, batches, fridges,
        mock(FridgeMemberRepository.class), mock(IngredientMasterRepository.class), access);
    final Fridge fridge = Fridge.builder().id(1L).build();
    final InventoryBatch stock = InventoryBatch.builder().id(10L).fridge(fridge).ingredientName("양파")
        .unitType(UnitType.QUANTITY).unit("개").remainingQuantity(2.0).status("ACTIVE")
        .consumeByDate(LocalDate.of(2026,10,10)).countBasis("낱개").perCountQuantity(180.0).perCountUnit("g").build();
    final ContainerSplitRequest request = mock(ContainerSplitRequest.class);
    @BeforeEach void setup() {
        when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).build()));
        when(fridges.findForUpdate(1L)).thenReturn(Optional.of(fridge));
        when(batches.findForUpdate(10L)).thenReturn(Optional.of(stock));
        when(containers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(batches.save(any())).thenAnswer(i -> i.getArgument(0));
        when(request.getFridgeId()).thenReturn(1L);
        when(request.getSourceBatchId()).thenReturn(10L);
        when(request.getQrCodeToken()).thenReturn("split-test");
        when(request.getContainerUnitType()).thenReturn(UnitType.QUANTITY);
        when(request.getSourceUnit()).thenReturn("개");
        when(request.getTransferredQuantity()).thenReturn(0.5);
        when(request.getContainerRemainingQuantity()).thenReturn(0.5);
    }
    @Test void splitPreservesTotalExpiryAndVerifiedConversion() {
        service.registerWithSplit(7L, request);
        var captor = org.mockito.ArgumentCaptor.forClass(InventoryBatch.class);
        verify(batches).save(captor.capture());
        var added = captor.getValue();
        assertEquals(1.5, stock.getRemainingQuantity());
        assertEquals(0.5, added.getRemainingQuantity());
        assertEquals(stock.getConsumeByDate(), added.getConsumeByDate());
        assertEquals(180.0, added.getPerCountQuantity());
        verify(access).requireAccess(7L, fridge);
        verify(access, never()).record(any(), any(), any(), any(), any(), any());
    }
    @Test void cannotInventExtraQuantity() {
        when(request.getContainerRemainingQuantity()).thenReturn(500.0);
        assertThrows(RuntimeException.class, () -> service.registerWithSplit(7L, request));
        assertEquals(2.0, stock.getRemainingQuantity());
        verify(batches, never()).save(any());
    }
    @Test void cannotChangeUnitOrOverdraw() {
        when(request.getSourceUnit()).thenReturn("g");
        assertThrows(RuntimeException.class, () -> service.registerWithSplit(7L, request));
        when(request.getSourceUnit()).thenReturn("개");
        when(request.getTransferredQuantity()).thenReturn(3.0);
        when(request.getContainerRemainingQuantity()).thenReturn(3.0);
        assertThrows(RuntimeException.class, () -> service.registerWithSplit(7L, request));
        assertEquals(2.0, stock.getRemainingQuantity());
    }
}
