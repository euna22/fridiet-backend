package com.example.fridiet.dto;

import com.example.fridiet.entity.UnitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class MealRecordRequest {
    @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9-]{8,64}")
    private String requestId;

    @NotNull(message = "냉장고 ID는 필수입니다.")
    private Long fridgeId;

    @NotBlank(message = "식사 제목은 필수입니다.")
    @jakarta.validation.constraints.Size(max = 100)
    private String title;

    @NotBlank(message = "식사 유형은 필수입니다.")
    @jakarta.validation.constraints.Pattern(regexp = "BREAKFAST|LUNCH|DINNER|SNACK")
    private String mealType; // BREAKFAST, LUNCH, DINNER, SNACK

    private String note;
    private String imageUrl;
    private LocalDateTime eatenAt;

    @Valid
    private List<MealItemDto> usedIngredients;

    @Getter
    @NoArgsConstructor
    public static class MealItemDto {

        @NotNull(message = "재고 배치 ID는 필수입니다.")
        private Long batchId;

        @NotNull(message = "단위 입력 유형은 필수입니다.")
        private UnitType unitType; // QUANTITY, PERCENT, CUSTOM

        // 1) QUANTITY 또는 PERCENT인 경우
        private Double quantityUsed;
        private String unit;

        // 2) CUSTOM인 경우
        private String customText;
    }
}
