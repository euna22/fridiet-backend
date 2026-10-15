package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "recipe_recommendation_feedback",
        indexes = {
                @Index(name = "idx_recipe_feedback_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_recipe_feedback_recipe_event", columnList = "recipe_name, event_type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeRecommendationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id")
    private Fridge fridge;

    @Column(name = "recipe_name", nullable = false, length = 100)
    private String recipeName;
    @Column(name = "recipe_key", length = 160)
    private String recipeKey;

    @Column(name = "event_key", length = 64, unique = true)
    private String eventKey;

    @Column(name = "meal_event_id", unique = true)
    private Long mealEventId;

    public void linkMeal(Long mealEventId) { this.mealEventId = mealEventId; }

    public void updateMeal(String name, LocalDateTime time) {
        if (mealEventId == null) throw new IllegalStateException("식사에 연결된 이력만 수정할 수 있습니다.");
        this.recipeName = name;
        this.createdAt = time;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private RecipeFeedbackEventType eventType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public RecipeRecommendationFeedback(
            User user,
            Fridge fridge,
            String recipeName,
            String recipeKey,
            String eventKey,
            RecipeFeedbackEventType eventType,
            LocalDateTime createdAt
    ) {
        this.user = user;
        this.fridge = fridge;
        this.recipeName = recipeName;
        this.recipeKey = recipeKey;
        this.eventKey = eventKey;
        this.eventType = eventType;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
