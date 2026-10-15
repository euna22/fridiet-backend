package com.example.fridiet.dto;

import com.example.fridiet.entity.RecipeFeedbackEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@lombok.AllArgsConstructor
public class RecipeFeedbackRequest {

    private Long fridgeId;
    @jakarta.validation.constraints.Size(max = 160)
    private String recipeKey;

    @NotBlank(message = "레시피 이름은 필수입니다.")
    @jakarta.validation.constraints.Size(max = 100)
    private String recipeName;

    @NotNull(message = "피드백 종류는 필수입니다.")
    private RecipeFeedbackEventType eventType;
}
