package com.example.fridiet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
public class AiRecipeClientDto {

    // AI에게 보낼 프롬프트 데이터
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Request {
        private boolean shoppingMode;
        private String healthCondition;
        private String healthGoal;
        private List<String> activeInventories; // 이전 AI 요청과의 호환용 재고명 목록
        private List<InventoryDto> inventories; // 소비기한/잔량을 포함한 재고 스냅샷
        private List<String> targetIngredients; // 반드시 활용할 재료
        private List<String> additionalIngredients; // 추가 희망 재료
        private List<String> excludeIngredients; // 제외 희망 재료
        private List<FeedbackDto> recentFeedback; // 최근 30일 추천·저장·조리·거절 이력
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class InventoryDto {
        private String countBasis;
        private Double perCountQuantity;
        private String perCountUnit;
        private boolean quantityNeedsReview;
        private String unitType;
        private String ingredientName;
        private LocalDate consumeByDate;
        private Double remainingQuantity;
        private String unit;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class FeedbackDto {
        private String recipeKey;
        private String recipeName;
        private String eventType;
        private LocalDateTime occurredAt;
    }

    // AI가 레시피 생성 후 백엔드로 돌려줄 JSON
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String recipeKey;
        private String title;
        private String description;
        private Integer cookingTime;
        private String category;
        private Double score;
        private Map<String, Double> scoreBreakdown;
        private List<String> reasons;
        private List<String> availableIngredients;
        private List<String> missingIngredients;
        private List<IngredientBalanceDto> ingredientBalances;
        private List<IngredientDto> ingredients;
        private List<StepDto> steps;

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class IngredientBalanceDto {
            private String name;
            private Double requiredQuantity;
            private Double knownAvailableQuantity;
            private Double shortageQuantity;
            private String unit;
            private String status;
        }

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class IngredientDto {
            private String nameText;
            private Double quantity;
            private String unit;
            private Boolean optional;
        }

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StepDto {
            private Integer stepNumber;
            private String description;
        }
    }
}
