package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipe_ingredient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_ingredient_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private IngredientMaster ingredientMaster; //nullable

    @Column(name = "name_text", nullable = false)
    private String nameText;

    private Double quantity;
    private String unit;
    private Boolean optional;

    @Builder
    public RecipeIngredient(Recipe recipe, IngredientMaster ingredientMaster, String nameText, Double quantity, String unit, Boolean optional) {
        this.recipe = recipe;
        this.ingredientMaster = ingredientMaster;
        this.nameText = nameText;
        this.quantity = quantity;
        this.unit = unit;
        this.optional = optional;
    }
}
