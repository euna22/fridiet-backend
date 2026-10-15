package com.example.fridiet.service;

import com.example.fridiet.dto.*;
import com.example.fridiet.entity.RecipeFeedbackEventType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CookingServiceTest {
    private final RecipeService recipes = mock(RecipeService.class);
    private final CalendarService calendar = mock(CalendarService.class);
    private final CookingService service = new CookingService(recipes, calendar);

    private MealRecordRequest meal() {
        var meal = mock(MealRecordRequest.class);
        when(meal.getRequestId()).thenReturn("cook-test-1234");
        when(meal.getMealType()).thenReturn("DINNER");
        when(meal.getFridgeId()).thenReturn(11L);
        when(meal.getTitle()).thenReturn("당근볶음");
        return meal;
    }

    @Test void completesMealAndFeedbackTogether() {
        var meal = meal();
        var result = MealRecordResponse.builder().mealEventId(99L).build();
        when(calendar.recordMealAndDeductStock(10L, meal, "mfds:1")).thenReturn(result);
        assertSame(result, service.complete(10L, new CookingCompleteRequest("mfds:1", meal)));
        verify(recipes).validateCookingRecipe(10L, 11L, "mfds:1");
    }

    @Test void requiresIdempotencyKey() {
        var meal = meal();
        when(meal.getRequestId()).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> service.complete(10L, new CookingCompleteRequest("mfds:1", meal)));
        verifyNoInteractions(recipes, calendar);
    }

    @Test void rejectsInvalidMealType() {
        var meal = meal();
        when(meal.getMealType()).thenReturn("INVALID");
        assertThrows(ResponseStatusException.class, () -> service.complete(10L, new CookingCompleteRequest("mfds:1", meal)));
        verifyNoInteractions(recipes, calendar);
    }

    @Test void unauthorizedRecipeCannotDeductStock() {
        doThrow(new IllegalArgumentException("not recommended")).when(recipes).validateCookingRecipe(anyLong(), anyLong(), anyString());
        assertThrows(IllegalArgumentException.class, () -> service.complete(10L, new CookingCompleteRequest("mfds:1", meal())));
        verifyNoInteractions(calendar);
    }
}
