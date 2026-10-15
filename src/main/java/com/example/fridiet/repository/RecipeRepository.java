package com.example.fridiet.repository;

import com.example.fridiet.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findAllByOwner_IdAndIsGeneratedTrueOrderByCreatedAtDesc(Long userId);
    java.util.Optional<Recipe> findFirstByOwner_IdAndRecipeKeyOrderByIdAsc(Long userId, String recipeKey);
}
