package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "meal_consumption")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consumption_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_event_id", nullable = false)
    private Calendar calendar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private InventoryBatch inventoryBatch;

    @Column(name = "quantity_used")
    private Double quantityUsed;

    @Column(name = "remaining_quantity")
    private Double remainingQuantity; // QUANTITY/PERCENT 차감 후 남은 수량

    @Column(name="custom_quantity_text")
    private String customQuantityText; // CUSTOM 차감 후 남은 재고 텍스트

    @Column(name="pre_custom_quantity_text")
    private String preCustomQuantityText; // CUSTOM 차감 전 재고 텍스트

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false)
    private UnitType unitType; // QUANTITY, PERCENT, CUSTOM

    /**
     * 과거 숫자 이력에 추가된 unit_type 컬럼이 CUSTOM으로 채워진 경우의 읽기 호환.
     * 1:1 소비 이력 대조를 통과한 식사만 허용하며 DB 원문/수량/단위는 변경하지 않는다.
     */
    public UnitType getUnitType() {
        if (unitType == UnitType.CUSTOM && quantityUsed != null && quantityUsed > 0
                && unit != null && !unit.isBlank() && customQuantityText == null && preCustomQuantityText == null
                && calendar != null && Boolean.TRUE.equals(calendar.getActivityLinked()))
            return UnitType.QUANTITY;
        return unitType;
    }

    private String unit; // QUANTITY일 때 단위, CUSTOM일 때 null 또는 단위 저장

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public MealConsumption(Calendar calendar, InventoryBatch inventoryBatch, Double quantityUsed, UnitType unitType, String customQuantityText, String preCustomQuantityText, Double remainingQuantity, String unit) {
        this.calendar = calendar;
        this.inventoryBatch = inventoryBatch;
        this.unitType = unitType;

        if(this.unitType == UnitType.CUSTOM) {
            this.preCustomQuantityText = preCustomQuantityText;
            this.customQuantityText = customQuantityText;
            this.quantityUsed = null;
            this.remainingQuantity = null;
        } else if(this.unitType == UnitType.QUANTITY || this.unitType == UnitType.PERCENT) {
            this.quantityUsed = quantityUsed;
            this.remainingQuantity = remainingQuantity;
            this.unit = unit;
        }
    }
}
