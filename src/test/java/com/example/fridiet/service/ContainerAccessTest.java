package com.example.fridiet.service;

import com.example.fridiet.entity.*;
import com.example.fridiet.dto.ContainerDeductRequest;
import com.example.fridiet.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ContainerAccessTest {
    private final ContainerRepository containers = mock(ContainerRepository.class);
    private final InventoryBatchRepository batches = mock(InventoryBatchRepository.class);
    private final InventoryActivityService access = mock(InventoryActivityService.class);
    private final ContainerService service = new ContainerService(mock(UserRepository.class), containers, batches,
            mock(FridgeRepository.class), mock(FridgeMemberRepository.class), mock(IngredientMasterRepository.class), access);

    @Test void scanRejectsOtherFridge() {
        Fridge fridge = Fridge.builder().id(2L).build();
        when(containers.findByQrCodeToken("test")).thenReturn(Optional.of(Container.builder().fridge(fridge).build()));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireAccess(1L, fridge);
        assertThrows(ResponseStatusException.class, () -> service.scanContainer(1L, "test"));
    }

    @Test void overdrawCannotChangeStockOrRecordConsumption() {
        Fridge fridge = Fridge.builder().id(2L).build();
        InventoryBatch batch = InventoryBatch.builder().id(3L).fridge(fridge).status("ACTIVE")
                .unitType(UnitType.QUANTITY).unit("개").remainingQuantity(2.0).build();
        Container container = Container.builder().fridge(fridge).build();
        container.linkInventoryBatch(batch);
        when(containers.findForUpdate("test")).thenReturn(Optional.of(container));
        when(batches.findForUpdate(3L)).thenReturn(Optional.of(batch));
        ContainerDeductRequest request = mock(ContainerDeductRequest.class);
        when(request.getUnitType()).thenReturn(UnitType.QUANTITY);
        when(request.getQuantityUsed()).thenReturn(3.0);
        assertThrows(IllegalArgumentException.class, () -> service.deductQuantityByQr(1L, "test", request));
        assertEquals(2.0, batch.getRemainingQuantity());
        verify(access, never()).record(any(), any(), any(), any(), any(), any());
    }
}
