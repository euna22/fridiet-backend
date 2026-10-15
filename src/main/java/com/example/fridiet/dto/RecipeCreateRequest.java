package com.example.fridiet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class RecipeCreateRequest {

    private Long fridgeId;
    @jakarta.validation.constraints.Size(max = 160)
    private String recipeKey;

    @NotBlank(message = "레시피 이름은 필수입니다.")
    private String recipeName;

    private Integer cookTime;
    private String description;
    private String memo;
    private String stepsJson;

    @NotEmpty(message = "최소 하나 이상의 재료를 등록해야 합니다.")
    @Valid
    private List<RecipeIngredientDto> ingredients;

    @Getter
    @NoArgsConstructor
    public static class RecipeIngredientDto {

        @NotBlank(message = "재료 이름은 필수입니다.")
        private String nameText;

        private Double quantity;
        private String unit;

        @NotNull(message = "선택 재료 여부를 입력해주세요.")
        private Boolean optional; // true: 선택 재료, false: 필수 재료
    }
}
