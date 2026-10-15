package com.example.fridiet.controller;

import com.example.fridiet.dto.InventoryCreateRequest;
import com.example.fridiet.dto.InventoryUpdateRequest;
import com.example.fridiet.dto.InventoryResponse;
import com.example.fridiet.service.InventoryManualService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventories")
@RequiredArgsConstructor
public class InventoryManualController {

    private final InventoryManualService inventoryManualService;
    private final com.example.fridiet.service.InventoryActivityService inventoryActivityService;

    /**
     * 재고 수동 직접 등록
     */
    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InventoryCreateRequest request) {

        InventoryResponse response = inventoryManualService.createInventory(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 특정 냉장고의 미소진 재고 목록 조회 및 키워드 검색
     */
    @GetMapping
    public ResponseEntity<List<InventoryResponse>> getActiveInventories(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long fridgeId,
            @RequestParam(required = false) String keyword) {
        List<InventoryResponse> response = inventoryManualService.getActiveInventories(userId, fridgeId, keyword);
        return ResponseEntity.ok(response);
    }

    /**
     * 재고 단건 상세 조회
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<InventoryResponse> getInventoryDetail(@AuthenticationPrincipal Long userId, @PathVariable Long batchId) {
        InventoryResponse response = inventoryManualService.getInventoryDetail(userId, batchId);
        return ResponseEntity.ok(response);
    }

    /**
     * 재고 정보/잔량 수정
     */
    @PatchMapping("/{batchId}")
    public ResponseEntity<InventoryResponse> updateInventory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long batchId,
            @RequestBody InventoryUpdateRequest request) {

        InventoryResponse response = inventoryManualService.updateInventory(userId, batchId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 재고 삭제
     */
    @DeleteMapping("/{batchId}")
    public ResponseEntity<Void> deleteInventory(@AuthenticationPrincipal Long userId, @PathVariable Long batchId,
                                                @RequestParam(defaultValue = "REMOVED") String reason) {
        inventoryActivityService.closeBatch(userId, batchId, reason);
        return ResponseEntity.noContent().build();
    }
}
