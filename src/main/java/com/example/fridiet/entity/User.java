package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "social_provider")
    private String socialProvider;

    @Column(name = "social_id")
    private String socialId;

    @Column(name = "name")
    private String name;

    @Column(name = "health_condition")
    private String healthCondition;

    @Column(name = "health_goal")
    private String healthGoal;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private String fcmToken;

    @Column(name = "main_fridge_id")
    private Long mainFridgeId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "push_enabled")
    private Boolean pushEnabled;

    @Column(name = "expiry_enabled")
    private Boolean expiryEnabled;

    @Column(name = "fridge_activity_enabled")
    private Boolean fridgeActivityEnabled;

    @Column(name = "notification_time")
    @Builder.Default
    private String notificationTime = "09:00";

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateMainFridge(Long fridgeId) {
        this.mainFridgeId = fridgeId;
    }

    public void linkSocial(String provider, String socialId) {
        this.socialProvider = provider;
        this.socialId = socialId;
    }

    public void updateSocialInfo(String socialId, String socialProvider) {
        this.socialId = socialId;
        this.socialProvider = socialProvider;
    }

    public void updatePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
    }

    // 회원 탈퇴 처리
    public void withdraw(String anonymizedEmail) {
        this.isDeleted = true;
        this.deletedAt = java.time.LocalDateTime.now();
        this.email = anonymizedEmail; // 동일 이메일 재가입 가능하도록 변경
        this.socialId = null;
    }

    public void updateProfile(String healthCondition, String healthGoal) {
        this.healthCondition = healthCondition;
        this.healthGoal = healthGoal;
    }

    public void updateNotificationSettings(Boolean pushEnabled, Boolean expiryEnabled, Boolean fridgeActivityEnabled, String notificationTime) {
        this.pushEnabled = pushEnabled;
        this.expiryEnabled = expiryEnabled;
        this.fridgeActivityEnabled = fridgeActivityEnabled;
        this.notificationTime = notificationTime;
    }
}
