package com.example.fridiet.dto;

import java.util.List;

public record MonthlyReportResponse(
        String month, String timezone, boolean partialMonth,
        int cookedCount, int savedRecipeCount, int activeDays, int uniqueCookedRecipes,
        List<RecipeCount> topRecipes, List<DailyCount> dailyActivity, String summary,
        List<String> notes, List<StockTotal> stockActivity) {
    public record StockTotal(String ingredientName, String unit, java.math.BigDecimal consumedQuantity,
                             java.math.BigDecimal discardedQuantity, int consumedRecords, int discardedRecords) {}
    public record RecipeCount(String recipeKey, String recipeName, int count) {}
    public record DailyCount(String date, int cookedCount, int savedCount) {}
}
