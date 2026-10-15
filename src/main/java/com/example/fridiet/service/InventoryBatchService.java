package com.example.fridiet.service;

import com.example.fridiet.dto.OcrIngredientItemRequest;
import com.example.fridiet.dto.OcrInventoryCreateRequest;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryBatchService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final FridgeRepository fridgeRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final IngredientMasterRepository ingredientMasterRepository;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final FcmNotificationRepository fcmNotificationRepository;
    private final FcmNotificationService fcmNotificationService;

    /**
     * 영수증 인식으로 자동 재고 등록
     */
    @Transactional
    public List<InventoryBatch> addOcrIngredients(Long userId, Long fridgeId, OcrInventoryCreateRequest request) {

        boolean hasAccess = fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId);
        if(!hasAccess) {
            throw new RuntimeException("해당 냉장고에 접근 권한이 없습니다.");
        }

        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 냉장고입니다."));

        List<InventoryBatch> savedBatches = new ArrayList<>();

        for(OcrIngredientItemRequest item : request.getItems()) {

            Optional<IngredientMaster> masterOpt = ingredientMasterRepository.findByCanonicalName(item.getIngredientName())
                    .or(() -> ingredientAliasRepository.findByAliasName(item.getIngredientName())
                                .map(IngredientAlias::getIngredientMaster));

            IngredientMaster master = masterOpt.orElse(null);

            String finalIngredientName = (master != null) ? master.getCanonicalName() : item.getIngredientName().trim();
            String finalUnit = (item.getUnit() != null && item.getUnit().trim().isEmpty())
                    ? item.getUnit().trim()
                    : (master != null ? master.getDefaultUnit() : "개");

            // 동일 냉장고 + 동일 식재료 + 동일 유통기한 + ACTIVE 상태의 기존 배치 검색
            // 구매별 배치를 유지한다. 개수와 무게를 합산하거나 이력을 섞지 않는다.

            // 수량 매핑
            InventoryBatch batch;
            {
                batch = InventoryBatch.builder()
                        .fridge(fridge)
                        .ingredientMaster(master)
                        .ingredientName(finalIngredientName)
                        .locationCategory(item.getLocationCategory())
                        .consumeByDate(item.getConsumeByDate())
                        .unitType(UnitType.QUANTITY)
                        .remainingQuantity(item.getQuantity())
                        .unit(finalUnit)
                        .status("ACTIVE")
                        .createdBy(userId)
                        .build();

                batch.normalizeQuantity(item);
                inventoryBatchRepository.save(batch);
            }

            savedBatches.add(batch);
        }

        if(!savedBatches.isEmpty()) {
            int totalCount = savedBatches.size();
            String firstIngredientName = savedBatches.get(0).getIngredientName();

            String displayTitle = (totalCount == 1)
                    ? String.format("냉장고에 %s(이)가 추가되었어요", firstIngredientName)
                    : String.format("냉장고에 %s 외 %d건이 추가되었어요", firstIngredientName, totalCount - 1);

            String message = String.format("%s에 새로운 재료가 추가되었습니다.", fridge.getName());

            List<FridgeMember> fridgeMembers = fridgeMemberRepository.findByFridgeIdWithUser(fridgeId);

            for(FridgeMember member : fridgeMembers) {
                User targetUser = member.getUser();

                if(targetUser == null) continue;
                if(targetUser.getId().equals(userId)) continue;

                if(Boolean.FALSE.equals(targetUser.getPushEnabled())) continue;
                if(Boolean.FALSE.equals(targetUser.getFridgeActivityEnabled())) continue;

                fcmNotificationRepository.save(FcmNotification.builder()
                        .userId(targetUser.getId())
                        .type("fridge")
                        .title(displayTitle)
                        .message(message)
                        .ingredientId(savedBatches.get(0).getId())
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            try {
                fcmNotificationService.sendNotificationToFridgeMembers(fridge.getId(), userId, displayTitle, message);
            } catch(Exception e) {
                log.error("[FCM 알림 전송 실패]: {}", e.getMessage());
            }
        }
        return savedBatches;
    }

    // 소비기한이 n일 이내로 남은 ACTIVE 재고 목록 조회
    public List<InventoryBatch> getExpiringInventoryBatches(Long userId, Long fridgeId, int daysToExpire) {

        // 요청한 유저가 해당 냉장고의 OWNER 또는 MEMBER인지 확인
        boolean hasAccess = fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId);
        if(!hasAccess) {
            throw new RuntimeException("해당 냉장고에 접근 권한이 없습니다.");
        }

        // 날짜 계산
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(daysToExpire); // 오늘 + n일

        return inventoryBatchRepository.findExpiringBatches(fridgeId, "ACTIVE", today, targetDate);
    }
}
