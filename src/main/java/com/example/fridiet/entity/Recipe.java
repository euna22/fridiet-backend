package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "recipe")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(name = "recipe_name", nullable = false)
    private String recipeName;
    @Column(name = "recipe_key", length = 160)
    private String recipeKey;

    @Column(name = "cook_time")
    private Integer cookTime;

    @Column(name = "is_generated", nullable = false)
    private Boolean isGenerated;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String memo;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson; // LLM 출력 및 단계별 레시피 저장을 위해 TEXT 구조 활용

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public Recipe(User owner, String recipeName, String recipeKey, Integer cookTime, Boolean isGenerated, String description, String memo, String stepsJson) {
        this.owner = owner;
        this.recipeName = recipeName;
        this.recipeKey = recipeKey;
        this.cookTime = cookTime;
        this.isGenerated = isGenerated;
        this.description = description;
        this.memo = memo;
        this.stepsJson = stepsJson;
    }
}
