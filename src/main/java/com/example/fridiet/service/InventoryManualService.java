package com.example.fridiet.service;

import com.example.fridiet.dto.InventoryCreateRequest;
import com.example.fridiet.dto.InventoryUpdateRequest;
import com.example.fridiet.dto.InventoryResponse;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryManualService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final FridgeRepository fridgeRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final IngredientMasterRepository ingredientMasterRepository;
    private final FcmNotificationRepository fcmNotificationRepository;
    private final FcmNotificationService fcmNotificationService;
    private final InventoryActivityService inventoryActivityService;
    private final ContainerRepository containerRepository;

    /**
     * 수동 재고 등록
     */
    @Transactional
    public InventoryResponse createInventory(Long userId, InventoryCreateRequest request) {
        Fridge fridge = fridgeRepository.findById(request.getFridgeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다. ID: " + request.getFridgeId()));

        String trimmedName = request.getIngredientName().trim();
        inventoryActivityService.requireAccess(userId, fridge);
        IngredientMaster ingredientMaster = ingredientMasterRepository.findByCanonicalName(trimmedName).orElse(null);

        // 단위 기본 세팅
        UnitType unitType = request.getUnitType() != null ? request.getUnitType() : UnitType.QUANTITY;
        Double remainingQuantity = null;
        String customQuantityText = null;
        String unit = request.getUnit();

        if (unitType == UnitType.CUSTOM) {
            customQuantityText = request.getCustomQuantityText();
        } else if (unitType == UnitType.PERCENT) {
            remainingQuantity = request.getRemainingQuantity() != null ? request.getRemainingQuantity() : 100.0;
            unit = "%";
        } else {
            remainingQuantity = request.getRemainingQuantity() != null ? request.getRemainingQuantity() : 1.0;
        }

        InventoryBatch batch = InventoryBatch.builder()
                .fridge(fridge)
                .ingredientMaster(ingredientMaster)
                .ingredientName(trimmedName)
                .unitType(unitType)
                .remainingQuantity(remainingQuantity)
                .customQuantityText(customQuantityText)
                .unit(unit)
                .consumeByDate(request.getConsumeByDate())
                .locationCategory(request.getLocationCategory() != null ? request.getLocationCategory() : "REFRIGERATED")
                .status("ACTIVE")
                .createdBy(userId)
                .build();

        if(unitType == UnitType.QUANTITY) {
            batch.normalizeQuantity(request);
        }

        InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

        List<FridgeMember> fridgeMembers = fridgeMemberRepository.findByFridgeIdWithUser(request.getFridgeId());

        String title = String.format("냉장고에 %s(이)가 추가되었어요", request.getIngredientName());
        String message = String.format("%s에 새로운 재료가 추가되었습니다.", fridge.getName());

        for(FridgeMember member : fridgeMembers) {
            User targetUser = member.getUser();

            if(targetUser == null) continue;
            if(targetUser.getId().equals(userId)) continue;

            if(Boolean.FALSE.equals(targetUser.getPushEnabled())) continue;
            if(Boolean.FALSE.equals(targetUser.getFridgeActivityEnabled())) continue;

            fcmNotificationRepository.save(FcmNotification.builder()
                            .userId(targetUser.getId())
                            .type("fridge")
                            .title(title)
                            .message(message)
                            .ingredientId(savedBatch.getId())
                            .isRead(false)
                            .createdAt(LocalDateTime.now())
                            .build());
        }

        try {
            fcmNotificationService.sendNotificationToFridgeMembers(fridge.getId(), userId, title, message);
        } catch(Exception e) {
            log.error("[FCM 알림 전송 실패]: {}", e.getMessage());
        }


        Container container = containerRepository.findByCurrentBatch_Id(batch.getId()).orElse(null);

        return InventoryResponse.from(savedBatch, container);
    }

    /**
     * 특정 냉장고의 미소진(ACTIVE, EXPIRED) 재고 목록 조회 및 키워드 검색
     */
    @Transactional(readOnly = true)
    public List<InventoryResponse> getActiveInventories(Long userId, Long fridgeId, String keyword) {
        inventoryActivityService.requireAccess(userId, fridgeRepository.findById(fridgeId).orElseThrow());
        List<InventoryBatch> activeBatches;

        if(keyword != null && !keyword.trim().isEmpty()) {
            activeBatches = inventoryBatchRepository.searchByFridgeIdAndKeyword(fridgeId, keyword.trim());
        } else {
            activeBatches = inventoryBatchRepository.findAllActiveByFridgeId(fridgeId);
        }

        return activeBatches.stream()
                .map(batch -> {
                    Container container = containerRepository
                            .findByCurrentBatch_Id(batch.getId())
                            .orElse(null);
                    return InventoryResponse.from(batch, container);
                }).collect(Collectors.toList());
    }

    /**
     * 재고 단건 조회
     */
    @Transactional(readOnly = true)
    public InventoryResponse getInventoryDetail(Long userId, Long batchId) {
        InventoryBatch batch = inventoryBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다. ID: " + batchId));
        inventoryActivityService.requireAccess(userId, batch.getFridge());
        Container container = containerRepository.findByCurrentBatch_Id(batch.getId()).orElse(null);
        return InventoryResponse.from(batch, container);
    }

    /**
     * 재고 정보/잔량 수정
     */
    @Transactional
    public InventoryResponse updateInventory(Long userId, Long batchId, InventoryUpdateRequest request) {
        InventoryBatch batch = inventoryBatchRepository.findForUpdate(batchId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다. ID: " + batchId));

        inventoryActivityService.requireAccess(userId, batch.getFridge());
        if (!java.util.Set.of("ACTIVE", "EXPIRED").contains(batch.getStatus()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "이미 처리된 재고는 다시 등록해주세요.");

        batch.updateInventoryInfo(
                request.getUnitType(),
                request.getRemainingQuantity(),
                request.getCustomQuantityText(),
                request.getUnit(),
                request.getConsumeByDate(),
                request.getLocationCategory()
        );

        if(request.getUnitType() == UnitType.QUANTITY) {
            batch.normalizeQuantity(request);
        }

        Container container = containerRepository.findByCurrentBatch_Id(batch.getId()).orElse(null);

        return InventoryResponse.from(batch, container);
    }

    /**
     * 재고 수동 삭제
     */
    @Transactional
    public void deleteInventory(Long batchId) {
        InventoryBatch batch = inventoryBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다. ID: " + batchId));

        batch.delete();
    }
}
