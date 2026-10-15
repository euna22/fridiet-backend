package com.example.fridiet.dto;

import com.example.fridiet.entity.Recipe;
import com.example.fridiet.entity.RecipeIngredient;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RecipeResponse {
    private Long recipeId;
    private String recipeKey;
    private Long ownerUserId;
    private String recipeName;
    private Integer cookTime;
    private Boolean isGenerated;
    private String description;
    private String memo;
    private String stepsJson;
    private List<RecipeIngredientResponse> ingredients;
    private LocalDateTime createdAt;
    private Double recommendationScore;
    private Map<String, Double> scoreBreakdown;
    private List<String> recommendationReasons;
    private List<String> availableIngredients;
    private List<String> missingIngredients;
    private List<AiRecipeClientDto.Response.IngredientBalanceDto> ingredientBalances;

    public static RecipeResponse from(Recipe recipe, List<RecipeIngredient> ingredients) {
        return from(recipe, ingredients, null);
    }

    public static RecipeResponse from(
            Recipe recipe,
            List<RecipeIngredient> ingredients,
            AiRecipeClientDto.Response recommendation
    ) {
        return RecipeResponse.builder()
                .recipeId(recipe.getId())
                .recipeKey(recipe.getRecipeKey())
                .ownerUserId(recipe.getOwner().getId())
                .recipeName(recipe.getRecipeName())
                .cookTime(recipe.getCookTime())
                .isGenerated(recipe.getIsGenerated())
                .description(recipe.getDescription())
                .memo(recipe.getMemo())
                .stepsJson(recipe.getStepsJson())
                .ingredients(ingredients.stream().map(RecipeIngredientResponse::from).toList())
                .createdAt(recipe.getCreatedAt())
                .recommendationScore(recommendation != null ? recommendation.getScore() : null)
                .scoreBreakdown(recommendation != null ? recommendation.getScoreBreakdown() : null)
                .recommendationReasons(recommendation != null ? recommendation.getReasons() : List.of())
                .availableIngredients(recommendation != null ? recommendation.getAvailableIngredients() : List.of())
                .missingIngredients(recommendation != null ? recommendation.getMissingIngredients() : List.of())
                .ingredientBalances(recommendation != null ? recommendation.getIngredientBalances() : List.of())
                .build();
    }

    public static RecipeResponse from(
            Recipe recipe,
            List<RecipeIngredient> ingredients,
            List<String> availableIngredients,
            List<String> missingIngredients
    ) {
        return RecipeResponse.builder()
                .recipeId(recipe.getId())
                .ownerUserId(recipe.getOwner().getId())
                .recipeName(recipe.getRecipeName())
                .cookTime(recipe.getCookTime())
                .isGenerated(recipe.getIsGenerated())
                .description(recipe.getDescription())
                .memo(recipe.getMemo())
                .stepsJson(recipe.getStepsJson())
                .ingredients(ingredients.stream().map(RecipeIngredientResponse::from).toList())
                .createdAt(recipe.getCreatedAt())
                .availableIngredients(availableIngredients != null ? availableIngredients : List.of())
                .missingIngredients(missingIngredients != null ? missingIngredients : List.of())
                .build();
    }

    public static RecipeResponse preview(
            Long userId,
            AiRecipeClientDto.Response recommendation,
            String stepsJson
    ) {
        List<RecipeIngredientResponse> previewIngredients = recommendation.getIngredients() == null
                ? List.of()
                : recommendation.getIngredients().stream()
                        .map(ingredient -> RecipeIngredientResponse.builder()
                                .id(null)
                                .ingredientMasterId(null)
                                .nameText(ingredient.getNameText())
                                .quantity(ingredient.getQuantity())
                                .unit(ingredient.getUnit())
                                .optional(ingredient.getOptional())
                                .build())
                        .toList();

        return RecipeResponse.builder()
                .recipeId(null)
                .recipeKey(recommendation.getRecipeKey())
                .ownerUserId(userId)
                .recipeName(recommendation.getTitle())
                .cookTime(recommendation.getCookingTime())
                .isGenerated(true)
                .description(recommendation.getDescription())
                .memo(null)
                .stepsJson(stepsJson)
                .ingredients(previewIngredients)
                .createdAt(null)
                .recommendationScore(recommendation.getScore())
                .scoreBreakdown(recommendation.getScoreBreakdown())
                .recommendationReasons(recommendation.getReasons())
                .availableIngredients(recommendation.getAvailableIngredients())
                .missingIngredients(recommendation.getMissingIngredients())
                .ingredientBalances(recommendation.getIngredientBalances())
                .build();
    }

    @Getter
    @Builder
    public static class RecipeIngredientResponse {
        private Long id;
        private Long ingredientMasterId;
        private String nameText;
        private Double quantity;
        private String unit;
        private Boolean optional;

        public static RecipeIngredientResponse from(RecipeIngredient entity) {
            return RecipeIngredientResponse.builder()
                    .id(entity.getId())
                    .ingredientMasterId(entity.getIngredientMaster() != null ? entity.getIngredientMaster().getId() : null)
                    .nameText(entity.getNameText())
                    .quantity(entity.getQuantity())
                    .unit(entity.getUnit())
                    .optional(entity.getOptional())
                    .build();
        }
    }
}
