package com.example.fridiet.repository;

import com.example.fridiet.entity.RecipeRecommendationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RecipeRecommendationFeedbackRepository
        extends JpaRepository<RecipeRecommendationFeedback, Long> {
    java.util.Optional<RecipeRecommendationFeedback> findByMealEventId(Long mealEventId);
    @org.springframework.data.jpa.repository.Query("select f from RecipeRecommendationFeedback f where f.user.id = :userId and f.createdAt >= :start and f.createdAt < :end and f.eventType in :types order by f.createdAt desc")
    List<RecipeRecommendationFeedback> findMonthlyActivity(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("start") LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") LocalDateTime end,
            @org.springframework.data.repository.query.Param("types") java.util.Collection<com.example.fridiet.entity.RecipeFeedbackEventType> types);
    boolean existsByEventKey(String eventKey);
    boolean existsByUser_IdAndRecipeKey(Long userId, String recipeKey);

    List<RecipeRecommendationFeedback> findByUser_IdAndEventTypeInAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId, java.util.Collection<com.example.fridiet.entity.RecipeFeedbackEventType> eventTypes,
            LocalDateTime createdAfter);

    List<RecipeRecommendationFeedback>
    findTop50ByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime createdAfter
    );
}
