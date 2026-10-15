package com.example.fridiet.controller;

import com.example.fridiet.dto.OcrInventoryCreateRequest;
import com.example.fridiet.entity.InventoryBatch;
import com.example.fridiet.service.InventoryBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventories/{fridgeId}/ocr")
@RequiredArgsConstructor
public class InventoryBatchController {

    private final InventoryBatchService inventoryBatchService;

    @PostMapping
    public ResponseEntity<?> addOcrIngredients(
            @AuthenticationPrincipal Long userId,
            @PathVariable("fridgeId") Long fridgeId,
            @RequestBody OcrInventoryCreateRequest request) {
        try {
            List<InventoryBatch> results = inventoryBatchService.addOcrIngredients(userId, fridgeId, request);

            return ResponseEntity.status(HttpStatus.CREATED).body(results);
        } catch(Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/expiring")
    public ResponseEntity<List<InventoryBatch>> getExpiringBatches(
            @AuthenticationPrincipal Long userId,
            @PathVariable("fridgeId") Long fridgeId,
            @RequestParam(name = "days", defaultValue = "3") int days) {

        List<InventoryBatch> expiringBatches = inventoryBatchService.getExpiringInventoryBatches(userId, fridgeId, days);

        return ResponseEntity.ok(expiringBatches);
    }
}
