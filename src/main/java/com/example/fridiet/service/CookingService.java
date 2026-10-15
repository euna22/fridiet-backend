package com.example.fridiet.service;

import com.example.fridiet.dto.*;
import com.example.fridiet.entity.RecipeFeedbackEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CookingService {
    private final RecipeService recipeService;
    private final CalendarService calendarService;

    /** 재고, 식사 기록, 추천 피드백을 하나의 트랜잭션으로 저장한다. */
    @Transactional
    public MealRecordResponse complete(Long userId, CookingCompleteRequest request) {
        var meal = request.meal();
        if (meal.getRequestId() == null || meal.getRequestId().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "중복 차감 방지용 요청 ID가 필요합니다.");
        if (!java.util.Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(meal.getMealType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 끼니를 선택해주세요.");
        // 추천받은 레시피인지, 냉장고 접근 권한이 있는지 기존 로직으로 검증한다.
        recipeService.validateCookingRecipe(userId, meal.getFridgeId(), request.recipeKey());
        return calendarService.recordMealAndDeductStock(userId, meal, request.recipeKey());
    }
}
