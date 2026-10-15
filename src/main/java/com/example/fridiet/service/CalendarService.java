package com.example.fridiet.service;

import com.example.fridiet.dto.MealRecordRequest;
import com.example.fridiet.dto.MealRecordResponse;
import com.example.fridiet.dto.MealRecordUpdateRequest;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarRepository calendarRepository;
    private final MealConsumptionRepository mealConsumptionRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final FridgeRepository fridgeRepository;
    private final UserRepository userRepository;
    private final InventoryActivityService inventoryActivityService;
    private final RecipeService recipeService;

    /**
     * 식사 기록 등록 및 관련 재고 차감
     */
    @Transactional
    public MealRecordResponse recordMealAndDeductStock(Long userId, MealRecordRequest request) {
        return recordMealAndDeductStock(userId, request, null);
    }

    @Transactional
    public MealRecordResponse recordMealAndDeductStock(Long userId, MealRecordRequest request, String recipeKey) {
        User user = userRepository.findForFeedbackUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));
        String requestKey = request.getRequestId() == null ? null : userId + ":" + request.getRequestId();
        if (requestKey != null) {
            var previous = calendarRepository.findByOwnerIdAndRequestKey(userId, requestKey);
            if (previous.isPresent()) {
                if (previous.get().getDeletedAt() != null) throw conflict("이미 취소된 조리 요청입니다. 새 요청으로 기록해주세요.");
                return MealRecordResponse.from(previous.get(),
                        mealConsumptionRepository.findAllByCalendarId(previous.get().getId()));
            }
        }
        Fridge fridge = fridgeRepository.findById(request.getFridgeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다. ID: " + request.getFridgeId()));

        inventoryActivityService.requireAccess(userId, fridge);
        LocalDateTime occurredAt = request.getEatenAt() != null ? request.getEatenAt()
                : LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        if (occurredAt.isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))))
            throw invalid("미래 식사 기록으로 재고를 차감할 수 없습니다.");
        java.util.Map<Long, InventoryBatch> locked = new java.util.HashMap<>();
        if (request.getUsedIngredients() != null) {
            // Deterministic locking prevents two meals from overspending the same stock.
            var sorted = request.getUsedIngredients().stream()
                    .sorted(java.util.Comparator.comparing(MealRecordRequest.MealItemDto::getBatchId)).toList();
            for (var item : sorted) {
                if (locked.containsKey(item.getBatchId())) throw invalid("동일 재고를 중복 선택할 수 없습니다.");
                InventoryBatch batch = inventoryBatchRepository.findForUpdate(item.getBatchId())
                        .orElseThrow(() -> invalid("재고를 찾을 수 없습니다."));
                if (!batch.getFridge().getId().equals(fridge.getId())) throw invalid("다른 냉장고의 재고입니다.");
                if (!java.util.Set.of("ACTIVE", "EXPIRED").contains(batch.getStatus())) throw invalid("이미 처리된 재고입니다.");
                if (batch.isQuantityNeedsReview() || batch.getUnitType() != UnitType.QUANTITY || !QuantityPolicy.canonical(batch.getUnit()))
                    throw invalid("먼저 해당 재고의 수량을 g·개·mL로 확인해주세요.");
                if (item.getUnitType() != batch.getUnitType()) throw invalid("재고의 단위 유형과 일치하지 않습니다.");
                if (batch.getUnitType() == UnitType.CUSTOM) {
                    if (item.getQuantityUsed() != null || item.getCustomText() == null || item.getCustomText().isBlank())
                        throw invalid("직접 입력 재고는 사용 후 상태를 입력해주세요. 소진 시 0을 입력합니다.");
                } else {
                    Double amount = item.getQuantityUsed();
                    if (QuantityPolicy.decimal(amount).signum() <= 0
                            || QuantityPolicy.decimal(amount).compareTo(QuantityPolicy.decimal(batch.getRemainingQuantity())) > 0)
                        throw invalid("사용량은 0보다 크고 현재 잔량 이하여야 합니다.");
                    if (!java.util.Objects.equals(item.getUnit(), batch.getUnit())) throw invalid("단위를 자동 변환하지 않습니다. 재고와 같은 단위를 사용해주세요.");
                }
                locked.put(batch.getId(), batch);
            }
        }

        // Calendar(식사 기록) 생성
        Calendar calendar = Calendar.builder()
                .requestKey(requestKey)
                .fridge(fridge)
                .owner(user)
                .title(request.getTitle())
                .mealType(request.getMealType())
                .note(request.getNote())
                .imageUrl(request.getImageUrl())
                .eatenAt(occurredAt)
                .build();

        Calendar savedCalendar = calendarRepository.save(calendar);
        savedCalendar.markActivityLinked();

        // 사용된 재고 차감 및 MealConsumption 기록
        List<MealConsumption> savedConsumptions = new ArrayList<>();

        if(request.getUsedIngredients() != null && !request.getUsedIngredients().isEmpty()) {
            for (MealRecordRequest.MealItemDto itemDto : request.getUsedIngredients()) {
                InventoryBatch batch = locked.get(itemDto.getBatchId());

                if("CONSUMED".equals(batch.getStatus())) {
                    throw new IllegalStateException("이미 소진된 재고입니다: " + batch.getIngredientName());
                }

                // 나중에 원복할 수 있도록 차감 전 텍스트 백업
                String preCustomText = batch.getCustomQuantityText();

                // 단위 유형별 재고 차감 및 업데이트 처리
                if(itemDto.getUnitType() == UnitType.CUSTOM) {
                    batch.consumeQuantity(null, itemDto.getCustomText());
                } else {
                    batch.consumeQuantity(itemDto.getQuantityUsed(), null);
                }

                // 소모 기록 저장
                MealConsumption consumption = MealConsumption.builder()
                        .calendar(savedCalendar)
                        .inventoryBatch(batch)
                        .unitType(itemDto.getUnitType())
                        .quantityUsed(QuantityPolicy.decimal(itemDto.getQuantityUsed()).doubleValue())
                        .remainingQuantity(batch.getRemainingQuantity())
                        .customQuantityText(batch.getCustomQuantityText())
                        .preCustomQuantityText(preCustomText)
                        .unit(itemDto.getUnitType() == UnitType.CUSTOM ? itemDto.getCustomText() : itemDto.getUnit())
                        .build();

                savedConsumptions.add(mealConsumptionRepository.save(consumption));
                inventoryActivityService.recordMeal(userId, batch, QuantityPolicy.decimal(itemDto.getQuantityUsed()).doubleValue(), occurredAt, savedCalendar.getId());
            }
        }

        if (recipeKey != null) recipeService.recordMealFeedback(savedCalendar, recipeKey);
        return MealRecordResponse.from(savedCalendar, savedConsumptions);
    }

    private static org.springframework.web.server.ResponseStatusException invalid(String message) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 삭제된 기록도 요청 키와 함께 보존하여 응답 유실 재시도가 재차감하지 않게 한다.
     */
    @Transactional
    public void deleteMeal(Long calendarId, Long userId) {
        Calendar calendar = lockOwnedMeal(calendarId, userId);
        if (calendar.getDeletedAt() != null) return;
        inventoryActivityService.requireAccess(userId, calendar.getFridge());
        List<MealConsumption> previous = mealConsumptionRepository.findAllByCalendarId(calendarId);
        var locked = lockMutationBatches(previous, List.of());
        var refunds = validateRefunds(calendar, previous, locked);
        restoreStock(refunds, locked);
        inventoryActivityService.cancelMeal(calendarId);
        calendar.markDeleted();
        recipeService.synchronizeMealFeedback(calendar, true);
    }

    @Transactional
    public void updateMeal(Long calendarId, Long userId, MealRecordUpdateRequest request) {
        Calendar calendar = lockOwnedMeal(calendarId, userId);
        if (calendar.getDeletedAt() != null) throw conflict("삭제된 식사 기록은 수정할 수 없습니다.");
        inventoryActivityService.requireAccess(userId, calendar.getFridge());
        LocalDateTime eatenAt = request.getEatenAt() == null ? calendar.getEatenAt() : request.getEatenAt();
        if (eatenAt == null || eatenAt.isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))))
            throw invalid("미래 식사 기록으로 재고를 차감할 수 없습니다.");
        if (!java.util.Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(
                request.getMealType() == null ? "" : request.getMealType()))
            throw invalid("올바른 끼니를 선택해주세요.");
        if (request.getTitle() == null || request.getTitle().isBlank()) throw invalid("식사 제목이 필요합니다.");

        List<MealConsumption> previous = mealConsumptionRepository.findAllByCalendarId(calendarId);
        // 생략은 유지, 빈 배열은 모든 사용량 취소. 메모 수정은 과거 수량을 재해석하지 않는다.
        List<MealRecordRequest.MealItemDto> items = request.getUsedIngredients();
        boolean usageChanged = items != null && !sameUsage(previous, items);
        if (usageChanged) {
            var locked = lockMutationBatches(previous, items);
            var refunds = validateRefunds(calendar, previous, locked);
            for (var item : items) {
                InventoryBatch batch = locked.get(item.getBatchId());
                var available = QuantityPolicy.decimal(batch.getRemainingQuantity())
                        .add(refunds.getOrDefault(batch.getId(), java.math.BigDecimal.ZERO));
                validateUsage(calendar.getFridge().getId(), batch, item, available,
                        refunds.containsKey(batch.getId()));
            }
            // 모든 배치와 새 사용량을 확인한 뒤 변경한다. 어느 단계든 실패하면 전체 롤백.
            restoreStock(refunds, locked);
            mealConsumptionRepository.deleteAllByCalendarId(calendarId);
            for (var item : items) {
                InventoryBatch batch = locked.get(item.getBatchId());
                double amount = QuantityPolicy.decimal(item.getQuantityUsed()).doubleValue();
                batch.consumeQuantity(amount, null);
                mealConsumptionRepository.save(MealConsumption.builder().calendar(calendar).inventoryBatch(batch)
                        .unitType(batch.getUnitType()).quantityUsed(amount).unit(batch.getUnit())
                        .remainingQuantity(batch.getRemainingQuantity()).build());
            }
        }
        calendar.updateInfo(request.getTitle(), request.getMealType(), request.getNote(),
                request.getImageUrl(), eatenAt);
        if (Boolean.TRUE.equals(calendar.getActivityLinked()) && usageChanged) {
            inventoryActivityService.cancelMeal(calendarId);
            for (var row : mealConsumptionRepository.findAllByCalendarId(calendarId))
                inventoryActivityService.recordMeal(userId, row.getInventoryBatch(), row.getQuantityUsed(), eatenAt, calendarId);
        } else if (Boolean.TRUE.equals(calendar.getActivityLinked())) {
            inventoryActivityService.rescheduleMeal(calendarId, eatenAt);
        }
        recipeService.synchronizeMealFeedback(calendar, false);
    }

    private Calendar lockOwnedMeal(Long calendarId, Long userId) {
        // 신규 조리/개인화 기록과 같은 사용자 -> 캘린더 -> 오름차순 배치 잠금 순서.
        userRepository.findForFeedbackUpdate(userId).orElseThrow(() -> invalid("사용자를 찾을 수 없습니다."));
        Calendar calendar = calendarRepository.findForUpdate(calendarId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "식사 기록을 찾을 수 없습니다."));
        if (!calendar.getOwner().getId().equals(userId))
            throw new AccessDeniedException("해당 식사 기록을 변경할 권한이 없습니다.");
        return calendar;
    }

    private java.util.Map<Long, InventoryBatch> lockMutationBatches(List<MealConsumption> previous,
            List<MealRecordRequest.MealItemDto> items) {
        java.util.Set<Long> ids = new java.util.TreeSet<>();
        java.util.Set<Long> requested = new java.util.HashSet<>();
        for (var item : items) {
            if (item == null || item.getBatchId() == null) throw invalid("재고 ID가 필요합니다.");
            if (!requested.add(item.getBatchId())) throw invalid("동일 재고를 중복 선택할 수 없습니다.");
            ids.add(item.getBatchId());
        }
        for (var row : previous) ids.add(row.getInventoryBatch().getId());
        java.util.Map<Long, InventoryBatch> locked = new java.util.LinkedHashMap<>();
        for (Long id : ids) locked.put(id, inventoryBatchRepository.findForUpdate(id)
                .orElseThrow(() -> conflict("기존 재고를 찾을 수 없어 사용량을 변경할 수 없습니다.")));
        return locked;
    }

    private java.util.Map<Long, java.math.BigDecimal> validateRefunds(Calendar calendar,
            List<MealConsumption> previous, java.util.Map<Long, InventoryBatch> locked) {
        java.util.Map<Long, java.math.BigDecimal> refunds = new java.util.HashMap<>();
        for (var row : previous) {
            InventoryBatch batch = locked.get(row.getInventoryBatch().getId());
            if (!batch.getFridge().getId().equals(calendar.getFridge().getId())
                    || !java.util.Set.of("ACTIVE", "EXPIRED", "CONSUMED").contains(batch.getStatus()))
                throw conflict("다른 냉장고 또는 이미 폐기된 재고는 자동 복원할 수 없습니다.");
            if (batch.isQuantityNeedsReview() || batch.getUnitType() != UnitType.QUANTITY
                    || row.getUnitType() != UnitType.QUANTITY || !QuantityPolicy.canonical(batch.getUnit())
                    || !java.util.Objects.equals(batch.getUnit(), row.getUnit()))
                throw conflict("기록 당시 단위와 현재 재고 단위가 다르거나 불명확합니다. 수량 확인이 필요합니다.");
            var amount = QuantityPolicy.decimal(row.getQuantityUsed());
            if (amount.signum() <= 0) throw conflict("기존 사용량이 불명확하여 자동 복원할 수 없습니다.");
            refunds.merge(batch.getId(), amount, java.math.BigDecimal::add);
        }
        refunds.forEach((id, amount) -> QuantityPolicy.decimal(
                QuantityPolicy.decimal(locked.get(id).getRemainingQuantity()).add(amount).doubleValue()));
        return refunds;
    }

    private void restoreStock(java.util.Map<Long, java.math.BigDecimal> refunds,
            java.util.Map<Long, InventoryBatch> locked) {
        refunds.forEach((id, amount) -> locked.get(id).restoreQuantity(amount.doubleValue(), null));
    }

    private void validateUsage(Long fridgeId, InventoryBatch batch, MealRecordRequest.MealItemDto item,
            java.math.BigDecimal available, boolean restoring) {
        if (!batch.getFridge().getId().equals(fridgeId)) throw invalid("다른 냉장고의 재고입니다.");
        if (!java.util.Set.of("ACTIVE", "EXPIRED").contains(batch.getStatus())
                && !(restoring && "CONSUMED".equals(batch.getStatus()))) throw invalid("이미 처리된 재고입니다.");
        if (batch.isQuantityNeedsReview() || batch.getUnitType() != UnitType.QUANTITY || !QuantityPolicy.canonical(batch.getUnit()))
            throw invalid("먼저 해당 재고의 수량을 g·개·mL로 확인해주세요.");
        if (item.getUnitType() != batch.getUnitType() || !java.util.Objects.equals(item.getUnit(), batch.getUnit()))
            throw invalid("재고와 같은 단위를 사용해주세요.");
        var amount = QuantityPolicy.decimal(item.getQuantityUsed());
        if (amount.signum() <= 0 || amount.compareTo(available) > 0)
            throw invalid("사용량은 0보다 크고 현재 잔량 이하여야 합니다.");
    }

    private boolean sameUsage(List<MealConsumption> previous, List<MealRecordRequest.MealItemDto> items) {
        if (previous.size() != items.size()) return false;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (var item : items) {
            if (item == null || item.getBatchId() == null || !seen.add(item.getBatchId())) return false;
            boolean matches = previous.stream().anyMatch(row -> row.getInventoryBatch().getId().equals(item.getBatchId())
                    && row.getUnitType() == item.getUnitType()
                    && java.util.Objects.equals(row.getUnit(), item.getUnit())
                    && java.util.Objects.equals(row.getQuantityUsed(), item.getQuantityUsed())
                    && java.util.Objects.equals(row.getCustomQuantityText(), item.getCustomText()));
            if (!matches) return false;
        }
        return true;
    }

    private static org.springframework.web.server.ResponseStatusException conflict(String message) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, message);
    }

    /**
     * 특정 사용자의 식사 기록 목록 조회
     */
    @Transactional(readOnly = true)
    public List<MealRecordResponse> getMealHistory(Long userId) {
        List<Calendar> calendarList = calendarRepository.findAllByOwnerIdAndDeletedAtIsNullOrderByEatenAtDesc(userId);

        return calendarList.stream().map(calendar -> {
            List<MealConsumption> consumptions = mealConsumptionRepository.findAllByCalendarId(calendar.getId());
            return MealRecordResponse.from(calendar, consumptions);
        }).toList();
    }
}
