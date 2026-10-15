package com.example.fridiet.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
public class AiRecipeGenerateRequest {

    @NotNull(message = "냉장고 ID는 필수입니다.")
    private Long fridgeId;

    // 추천 기준이 되는 사용자 ID
    private Long targetUserId;

    // 이전 클라이언트 호환용 필드. 추천에는 로그인 사용자의 DB 프로필을 사용한다.
    private String healthCondition;
    private String healthGoal;

    // 반드시 활용할 재료
    private List<String> targetIngredients;

    // 추가하고 싶은 재료
    private List<String> additionalIngredients;

    // 제외하고 싶은 재료
    private List<String> excludeIngredients;
}
