package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Calendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meal_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String title;

    @Column(name = "meal_type", nullable = false)
    private String mealType;

    private String note;
    @Column(name = "request_key", length = 100, unique = true)
    private String requestKey;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    @Column(name = "activity_linked")
    private Boolean activityLinked;

    public void markActivityLinked() { this.activityLinked = true; }

    public void markDeleted() {
        this.deletedAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
    }

    @Column(length = 1000)
    private String imageUrl;

    @Column(name = "eaten_at")
    private LocalDateTime eatenAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Calendar(Fridge fridge, User owner, String title, String mealType, String note, String imageUrl, LocalDateTime eatenAt, String requestKey) {
        this.requestKey = requestKey;
        this.fridge = fridge;
        this.owner = owner;
        this.title = title;
        this.mealType = mealType;
        this.note = note;
        this.imageUrl = imageUrl;
        this.eatenAt = (eatenAt != null) ? eatenAt : LocalDateTime.now();
    }

    public void updateInfo(String title, String mealType, String note, String imageUrl, LocalDateTime eatenAt) {
        this.title = title;
        this.mealType = mealType;
        this.note = note;
        this.imageUrl = imageUrl;
        this.eatenAt = eatenAt;
    }
}
