package com.example.fridiet.controller;

import com.example.fridiet.dto.RecipeCreateRequest;
import com.example.fridiet.dto.AiRecipeGenerateRequest;
import com.example.fridiet.dto.RecipeResponse;
import com.example.fridiet.dto.RecipeFeedbackRequest;
import com.example.fridiet.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @PostMapping("/ai/shopping")
    public RecipeResponse shopping(@AuthenticationPrincipal Long userId,
                                   @Valid @RequestBody AiRecipeGenerateRequest request) {
        return recipeService.shoppingRecommendation(userId, request);
    }

    /**
     * 사용자가 수동으로 레시피 작성 및 저장
     */
    @PostMapping("/manual")
    public ResponseEntity<RecipeResponse> createManualRecipe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecipeCreateRequest request) {

        RecipeResponse response = recipeService.createManualRecipe(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * AI 기반 레시피 생성 및 저장
     */
    @PostMapping("/ai")
    public ResponseEntity<RecipeResponse> generateAiRecipe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AiRecipeGenerateRequest request) {

        RecipeResponse response = recipeService.generateAiRecipe(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * AI 추천 미리보기. DB에는 저장하지 않는다.
     */
    @PostMapping("/ai/preview")
    public ResponseEntity<RecipeResponse> previewAiRecipe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AiRecipeGenerateRequest request) {

        return ResponseEntity.ok(recipeService.previewAiRecipe(userId, request));
    }

    /**
     * 사용자가 선택한 AI 추천 레시피를 저장한다.
     */
    @PostMapping("/ai/save")
    public ResponseEntity<RecipeResponse> saveAiRecipe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecipeCreateRequest request) {

        RecipeResponse response = recipeService.saveAiRecipe(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 저장한 AI 레시피 불러오기
     */
    @GetMapping("/ai/saved")
    public ResponseEntity<List<RecipeResponse>> getSavedAiRecipes(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long fridgeId) {

        List<RecipeResponse> response = recipeService.getSavedAiRecipes(userId, fridgeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{recipeId}")
    public ResponseEntity<RecipeResponse> getRecipeDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recipeId,
            @RequestParam Long fridgeId) {

        RecipeResponse response = recipeService.getRecipeDetail(userId, fridgeId, recipeId);
        return ResponseEntity.ok(response);
    }

    /**
     * 사용자의 조리 완료 또는 관심 없음 피드백을 기록한다.
     */
    @PostMapping("/ai/feedback")
    public ResponseEntity<Void> recordAiRecommendationFeedback(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecipeFeedbackRequest request) {

        recipeService.recordRecommendationFeedback(userId, request);
        return ResponseEntity.noContent().build();
    }
}
