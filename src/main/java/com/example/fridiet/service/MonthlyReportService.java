package com.example.fridiet.service;

import com.example.fridiet.dto.MonthlyReportResponse;
import com.example.fridiet.entity.RecipeFeedbackEventType;
import com.example.fridiet.entity.RecipeRecommendationFeedback;
import com.example.fridiet.repository.RecipeRecommendationFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service
public class MonthlyReportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private final RecipeRecommendationFeedbackRepository repository;
    private final Clock clock;
    private final com.example.fridiet.repository.InventoryActivityRepository inventoryActivities;

    @org.springframework.beans.factory.annotation.Autowired
    public MonthlyReportService(RecipeRecommendationFeedbackRepository repository, com.example.fridiet.repository.InventoryActivityRepository inventoryActivities) {
        this(repository, inventoryActivities, Clock.system(ZONE));
    }
    MonthlyReportService(RecipeRecommendationFeedbackRepository repository, com.example.fridiet.repository.InventoryActivityRepository inventoryActivities, Clock clock) {
        this.repository = repository;
        this.inventoryActivities = inventoryActivities;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse getReport(Long userId, String requestedMonth) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZONE));
        YearMonth current = YearMonth.from(now);
        YearMonth month;
        try {
            if (requestedMonth != null && !requestedMonth.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException();
            month = requestedMonth == null ? current : YearMonth.parse(requestedMonth);
            if (month.getYear() < 2000 || month.isAfter(current)) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2000년부터 이번 달까지 YYYY-MM 형식으로 요청해주세요.");
        }
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        if (end.isAfter(now)) end = now;
        var rows = repository.findMonthlyActivity(userId, start, end,
                List.of(RecipeFeedbackEventType.COOKED, RecipeFeedbackEventType.SAVED));
        Set<String> cooked = new HashSet<>(), saved = new HashSet<>();
        Map<String, MonthlyReportResponse.RecipeCount> ranking = new HashMap<>();
        Map<LocalDate, int[]> days = new TreeMap<>();
        for (var row : rows) {
            // Defensive boundaries also protect against malformed legacy/future records.
            if (row.getCreatedAt().isBefore(start) || !row.getCreatedAt().isBefore(end)) continue;
            String identity = row.getRecipeKey() != null ? row.getRecipeKey() : "legacy:" + row.getRecipeName().trim();
            LocalDate date = row.getCreatedAt().toLocalDate();
            if (row.getEventType() == RecipeFeedbackEventType.COOKED && cooked.add(identity + "|" + date)) {
                days.computeIfAbsent(date, ignored -> new int[2])[0]++;
                var previous = ranking.get(identity);
                ranking.put(identity, new MonthlyReportResponse.RecipeCount(row.getRecipeKey(),
                        previous == null ? row.getRecipeName() : previous.recipeName(), previous == null ? 1 : previous.count() + 1));
            } else if (row.getEventType() == RecipeFeedbackEventType.SAVED && saved.add(identity)) {
                days.computeIfAbsent(date, ignored -> new int[2])[1]++;
            }
        }
        var top = ranking.values().stream().sorted(Comparator.comparingInt(MonthlyReportResponse.RecipeCount::count).reversed()
                .thenComparing(MonthlyReportResponse.RecipeCount::recipeName)
                .thenComparing(r -> r.recipeKey() == null ? "" : r.recipeKey())).limit(5).toList();
        List<MonthlyReportResponse.DailyCount> daily = new ArrayList<>();
        int lastDay = month.equals(current) ? now.getDayOfMonth() : month.lengthOfMonth();
        for (int day = 1; day <= lastDay; day++) {
            LocalDate date = month.atDay(day);
            int[] counts = days.getOrDefault(date, new int[2]);
            daily.add(new MonthlyReportResponse.DailyCount(date.toString(), counts[0], counts[1]));
        }
        String summary = cooked.isEmpty() && saved.isEmpty() ? "아직 이번 달에 저장하거나 요리 완료한 기록이 없어요."
                : "선택한 달에 " + days.size() + "일 활동했고, " + ranking.size() + "가지 요리에 완료 기록을 남겼어요.";
        return new MonthlyReportResponse(month.toString(), ZONE.getId(), month.equals(current), cooked.size(), saved.size(),
                days.size(), ranking.size(), top, daily, summary, List.of(
                "로그인한 사용자의 기록입니다. 공유 냉장고 전체 통계가 아닙니다.",
                "요리 완료는 같은 요리·한국 날짜당 1건이며 실제 식사 횟수나 섭취량이 아닙니다.",
                "저장 수는 이 달 저장 이력이 있는 서로 다른 추천 레시피 수입니다. 수동 작성은 제외됩니다.",
                "기록이 없는 날은 미기록이며 실제로 요리하지 않았다는 뜻은 아닙니다.",
                "식재료 소비·폐기는 서버 이력 기능 도입 후 입력된 기록만 표시합니다. 과거 삭제·기기 내 기록은 포함하지 않습니다.",
                "식재료 양은 같은 이름·동일 단위만 합산합니다. 비율/직접 입력/단위 미상은 건수만 표시합니다.",
                "활동일은 저장·요리 완료 기준입니다. 영양량·폐기율·절약 금액은 추정하지 않습니다."), stockTotals(userId, start, end));
    }

    private List<MonthlyReportResponse.StockTotal> stockTotals(Long userId, LocalDateTime start, LocalDateTime end) {
        Map<List<String>, MonthlyReportResponse.StockTotal> totals = new LinkedHashMap<>();
        for (var event : inventoryActivities.findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(userId, start, end)) {
            if (event.getCancelledAt() != null) continue;
            boolean numeric = event.getUnitType() == com.example.fridiet.entity.UnitType.QUANTITY
                    && event.getUnit() != null && !event.getUnit().isBlank() && event.getQuantity() != null
                    && Double.isFinite(event.getQuantity()) && event.getQuantity() > 0;
            String unit = numeric ? event.getUnit() : "수량 미집계";
            var key = List.of(event.getIngredientName(), unit);
            var old = totals.getOrDefault(key, new MonthlyReportResponse.StockTotal(event.getIngredientName(), unit,
                    numeric ? java.math.BigDecimal.ZERO : null, numeric ? java.math.BigDecimal.ZERO : null, 0, 0));
            boolean consumed = "CONSUMED".equals(event.getEventType());
            boolean discarded = "DISCARDED".equals(event.getEventType());
            if (!consumed && !discarded) continue;
            var amount = numeric ? java.math.BigDecimal.valueOf(event.getQuantity()) : null;
            totals.put(key, new MonthlyReportResponse.StockTotal(old.ingredientName(), unit,
                    numeric && consumed ? old.consumedQuantity().add(amount) : old.consumedQuantity(),
                    numeric && discarded ? old.discardedQuantity().add(amount) : old.discardedQuantity(),
                    old.consumedRecords() + (consumed ? 1 : 0), old.discardedRecords() + (discarded ? 1 : 0)));
        }
        return totals.values().stream().sorted(Comparator.comparing(MonthlyReportResponse.StockTotal::ingredientName)
                .thenComparing(MonthlyReportResponse.StockTotal::unit)).toList();
    }
}
