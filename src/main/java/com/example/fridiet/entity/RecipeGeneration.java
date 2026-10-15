package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "recipe_generation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "generation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "prompt_json", columnDefinition = "TEXT")
    private String promptJson;

    @Column(name = "input_context_json", columnDefinition = "TEXT")
    private String inputContextJson;

    @Column(name = "output_raw_json", columnDefinition = "TEXT")
    private String outputRawJson;

    private Integer score;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
