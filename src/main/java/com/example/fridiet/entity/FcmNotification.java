package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FcmNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String type; // expiry, fridge

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    private Long ingredientId;

    @Column(name = "ingredient_name_text")
    private String ingredientNameText;

    @Builder.Default
    private boolean isRead = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public void markAsRead() {
        this.isRead = true;
    }
}
