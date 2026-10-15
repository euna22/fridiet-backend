package com.example.fridiet.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record CookingSuggestionRequest(@NotNull Long fridgeId,
        @NotNull @Size(max=150) List<@Valid Ingredient> ingredients) {
    public record Ingredient(@NotBlank String nameText, @NotNull Double quantity, @NotBlank String unit, boolean optional) {}
}
