package com.example.fridiet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CookingCompleteRequest(
        @NotBlank @Size(max = 160) String recipeKey,
        @NotNull @Valid MealRecordRequest meal) {}
