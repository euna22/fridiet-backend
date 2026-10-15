package com.example.fridiet.service;
import com.example.fridiet.dto.*;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class CookingSuggestionService {
    private final InventoryBatchRepository batches;
    private final FridgeRepository fridges;
    private final InventoryActivityService access;
    private final ContainerRepository containerRepository;
    public record Suggestion(InventoryResponse stock, BigDecimal quantityUsed, BigDecimal remainingAfter, String reason) {}
    public record Result(List<Suggestion> items, List<String> warnings) {}
    public static BigDecimal factor(InventoryBatch b, String target) {
        if (b.isQuantityNeedsReview() || b.getUnitType() != UnitType.QUANTITY) return null;
        if (Objects.equals(b.getUnit(), target)) {
            if ("개".equals(target) && b.getCountBasis() != null && !b.getCountBasis().isBlank()) return null;
            return BigDecimal.ONE;
        }
        if ("개".equals(b.getUnit()) && Objects.equals(b.getPerCountUnit(), target) && b.getPerCountQuantity() != null)
            return QuantityPolicy.decimal(b.getPerCountQuantity());
        return null;
    }
    @Transactional(readOnly=true)
    public Result suggest(Long userId, CookingSuggestionRequest request) {
        access.requireAccess(userId, fridges.findById(request.fridgeId()).orElseThrow());
        var stocks = batches.findAllActiveByFridgeId(request.fridgeId()).stream()
                .filter(b -> "ACTIVE".equals(b.getStatus()) && (b.getConsumeByDate() == null || !b.getConsumeByDate().isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))))
                .sorted(Comparator.comparing(InventoryBatch::getConsumeByDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(InventoryBatch::getId)).toList();
        Map<Long, BigDecimal> used = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        for (var ingredient : request.ingredients()) {
            String name = ingredient.nameText().trim(); names.add(name);
            if (ingredient.optional()) continue;
            QuantityPolicy.Amount need;
            try { need = QuantityPolicy.normalize(ingredient.quantity(), ingredient.unit()); }
            catch (org.springframework.web.server.ResponseStatusException e) { warnings.add(name + ": 레시피 단위 확인 필요"); continue; }
            BigDecimal left = need.quantity();
            for (var stock : stocks) {
                if (!name.equals(stock.getIngredientName().trim())) continue;
                BigDecimal f = factor(stock, need.unit());
                if (f == null || f.signum() <= 0 || stock.getRemainingQuantity() == null) continue;
                BigDecimal available = QuantityPolicy.decimal(stock.getRemainingQuantity()).subtract(used.getOrDefault(stock.getId(), BigDecimal.ZERO));
                BigDecimal take = left.divide(f, 6, RoundingMode.DOWN).min(available);
                if (take.signum() <= 0) continue;
                used.merge(stock.getId(), take, BigDecimal::add);
                left = left.subtract(take.multiply(f));
                if (left.signum() <= 0) break;
            }
            if (left.signum() > 0) warnings.add(name + ": 부족하거나 단위 환산 정보가 없어 사용량 확인 필요");
        }
        List<Suggestion> items = new ArrayList<>();
        for (var stock : stocks) {
            if (!names.contains(stock.getIngredientName().trim())) continue;
            var amount = used.getOrDefault(stock.getId(), BigDecimal.ZERO).setScale(6);
            var after = stock.getRemainingQuantity() == null ? null : QuantityPolicy.decimal(stock.getRemainingQuantity()).subtract(amount);
            items.add(new Suggestion(InventoryResponse.from(stock, null), amount, after,
                    amount.signum() > 0 ? "확인된 단위 기준 · 소비기한 순" : "자동 사용량 없음 · 직접 확인"));
        }
        return new Result(items, warnings);
    }
}
