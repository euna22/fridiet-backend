package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Immutable snapshot: editing or retiring a batch must not rewrite consumption history. */
@Entity
@Table(name = "inventory_activity", indexes = @Index(name = "idx_inventory_activity_user_time", columnList = "user_id,occurred_at"))
@Getter @Builder @AllArgsConstructor @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "fridge_id", nullable = false) private Long fridgeId;
    @Column(name = "batch_id", nullable = false) private Long batchId;
    @Column(name = "meal_event_id") private Long mealEventId;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    public void cancel() { cancelledAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")); }
    public void reschedule(LocalDateTime time) { occurredAt = time; }
    @Column(nullable = false) private String ingredientName;
    @Column(nullable = false) private String eventType;
    @Enumerated(EnumType.STRING) private UnitType unitType;
    private Double quantity;
    private String unit;
    private String customText;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;
}
