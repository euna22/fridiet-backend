package com.example.fridiet.controller;

import com.example.fridiet.dto.CookingCompleteRequest;
import com.example.fridiet.dto.MealRecordResponse;
import com.example.fridiet.service.CookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipes")
public class CookingController {
    private final CookingService cookingService;
    private final com.example.fridiet.service.CookingSuggestionService suggestions;
    @PostMapping("/ai/consumption-preview")
    public com.example.fridiet.service.CookingSuggestionService.Result preview(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody com.example.fridiet.dto.CookingSuggestionRequest request) {
        return suggestions.suggest(userId, request);
    }

    @PostMapping("/ai/complete")
    public MealRecordResponse complete(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody CookingCompleteRequest request) {
        return cookingService.complete(userId, request);
    }
}
