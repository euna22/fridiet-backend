package com.example.fridiet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class MealRecordUpdateRequest {

    @NotBlank(message = "식사 제목은 필수입니다.")
    @jakarta.validation.constraints.Size(max = 100)
    private String title;

    @NotBlank(message = "식사 유형은 필수입니다.")
    @jakarta.validation.constraints.Pattern(regexp = "BREAKFAST|LUNCH|DINNER|SNACK")
    private String mealType;

    private String note;
    private String imageUrl;
    private LocalDateTime eatenAt;

    @Valid
    private List<MealRecordRequest.MealItemDto> usedIngredients;
}
