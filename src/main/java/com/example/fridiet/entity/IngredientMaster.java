package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingredient_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingredient_id")
    private Long id;

    @Column(name = "ingredient_type", nullable = false)
    private String ingredientType; // INGREDIENT, SEASONING, BEVERAGE 등

    @Column(name = "canonical_name", nullable = false)
    private String canonicalName; // 표준 이름(우유, 간장 등)

    @Column(name = "default_unit")
    private String defaultUnit; // 기본 단위(ml, g, 개 등)

    @Column(name = "category")
    private String category; // 상세 카테고리(유제품, 채소, 육류 등)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
