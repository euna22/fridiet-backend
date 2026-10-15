package com.example.fridiet.service;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;

@Service @RequiredArgsConstructor
public class InventoryActivityService {
    private final InventoryBatchRepository batches;
    private final InventoryActivityRepository activities;
    private final FridgeMemberRepository members;

    public void requireAccess(Long userId, Fridge fridge) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!userId.equals(fridge.getOwner().getId()) && !members.existsByFridgeIdAndUserIdAndStatus(fridge.getId(), userId, "ACCEPTED"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "참여 중인 냉장고만 사용할 수 있습니다.");
    }

    public void record(Long userId, InventoryBatch batch, String type, Double quantity, String customText, LocalDateTime time) {
        activities.save(InventoryActivity.builder().userId(userId).fridgeId(batch.getFridge().getId()).batchId(batch.getId())
                .ingredientName(batch.getIngredientName()).eventType(type).quantity(quantity)
                .unitType(batch.getUnitType()).unit(batch.getUnit()).customText(customText).occurredAt(time).build());
    }

    public void recordMeal(Long userId, InventoryBatch batch, Double quantity, LocalDateTime time, Long mealEventId) {
        activities.save(InventoryActivity.builder().userId(userId).fridgeId(batch.getFridge().getId()).batchId(batch.getId())
                .mealEventId(mealEventId).ingredientName(batch.getIngredientName()).eventType("CONSUMED")
                .quantity(quantity).unitType(batch.getUnitType()).unit(batch.getUnit()).occurredAt(time).build());
    }

    /** 원본 이력을 보존하되 명시적으로 취소한 식사의 소비량은 집계에서 제외한다. */
    public void cancelMeal(Long mealEventId) {
        activities.findByMealEventIdAndCancelledAtIsNull(mealEventId).forEach(InventoryActivity::cancel);
    }

    public void rescheduleMeal(Long mealEventId, LocalDateTime time) {
        activities.findByMealEventIdAndCancelledAtIsNull(mealEventId).forEach(event -> event.reschedule(time));
    }

    @Transactional
    public void closeBatch(Long userId, Long batchId, String reason) {
        if (!java.util.Set.of("CONSUMED", "DISCARDED", "REMOVED").contains(reason))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "소진 또는 폐기 사유를 선택해주세요.");
        InventoryBatch batch = batches.findForUpdate(batchId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireAccess(userId, batch.getFridge());
        if (reason.equals(batch.getStatus())) return; // Safe retry while the same batch is locked.
        if (!java.util.Set.of("ACTIVE", "EXPIRED").contains(batch.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 재고입니다.");
        if (!reason.equals("REMOVED")) record(userId, batch, reason, batch.getRemainingQuantity(), batch.getCustomQuantityText(),
                LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        batch.closeAs(reason);
    }
}
