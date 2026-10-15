package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_batch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private IngredientMaster ingredientMaster;

    @Column(name = "ingredient_name", nullable = false)
    private String ingredientName;

    @Column(name = "location_category")
    private String locationCategory; // REFRIGERATED, FROZEN, ROOM_TEMP

    @Column(name = "consume_by_date", nullable = false)
    private LocalDate consumeByDate;

    @Convert(converter = QuantityDecimalConverter.class)
    @Column(name = "remaining_quantity", precision=18, scale=6)
    private Double remainingQuantity; // QUANTITY 또는 PERCENT일 때 숫자값 저장

    @Column(name="custom_quantity_text")
    private String customQuantityText; // CUSTOM일 때 문자열 저장

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false)
    private UnitType unitType; // QUANTITY, PERCENT, CUSTOM

    private String unit; // QUANTITY일 때 단위, CUSTOM일 때 null 또는 단위 저장

    private String countBasis;
    @Convert(converter = QuantityDecimalConverter.class)
    @Column(precision=18, scale=6)
    private Double perCountQuantity;
    private String perCountUnit;
    private boolean quantityNeedsReview;

    public void normalizeQuantity(com.example.fridiet.dto.QuantityMetadata metadata) {
        if (unitType != UnitType.QUANTITY) throw com.example.fridiet.service.QuantityPolicy.invalid("g·개·mL로 잔량을 확인해주세요.");
        if (metadata != null && (metadata.getPackageCount() != null || metadata.getPackageQuantity() != null || metadata.getPackageUnit() != null)) {
            var count = com.example.fridiet.service.QuantityPolicy.decimal(metadata.getPackageCount());
            var contents = com.example.fridiet.service.QuantityPolicy.normalize(metadata.getPackageQuantity(), metadata.getPackageUnit());
            remainingQuantity = count.multiply(contents.quantity()).doubleValue();
            unit = contents.unit();
        }
        // 포장/개체의 원래 기준을 지우지 않는다.
        if (java.util.Set.of("봉", "봉지", "팩", "병", "캔", "통", "알", "마리", "대", "쪽", "모", "장").contains(unit == null ? "" : unit)) {
            countBasis = unit + " 기준";
            unit = "개";
        }
        var normalized = com.example.fridiet.service.QuantityPolicy.normalize(remainingQuantity, unit);
        remainingQuantity = normalized.quantity().doubleValue();
        unit = normalized.unit();
        if (metadata != null && metadata.hasMetadataUpdates()) {
            if (metadata.getCountBasis() != null) countBasis = metadata.getCountBasis().trim();
            perCountQuantity = metadata.getPerCountQuantity();
            perCountUnit = metadata.getPerCountUnit();
        }
        if (!"개".equals(unit) && (metadata == null || !metadata.hasMetadataUpdates())) {
            countBasis = null; perCountQuantity = null; perCountUnit = null;
        }
        if (countBasis != null && countBasis.length() > 100) throw com.example.fridiet.service.QuantityPolicy.invalid("개수 기준 설명은 100자 이내로 입력해주세요.");
        if (perCountQuantity != null || perCountUnit != null) {
            if (!"개".equals(unit)) throw com.example.fridiet.service.QuantityPolicy.invalid("개당 용량은 개수형 재고에만 입력해주세요.");
            var per = com.example.fridiet.service.QuantityPolicy.normalize(perCountQuantity, perCountUnit);
            if ("개".equals(per.unit())) throw com.example.fridiet.service.QuantityPolicy.invalid("개당 용량은 g 또는 mL입니다.");
            perCountQuantity = per.quantity().doubleValue();
            perCountUnit = per.unit();
        }
        if (!"개".equals(unit)) { countBasis = null; perCountQuantity = null; perCountUnit = null; }
        customQuantityText = null;
        quantityNeedsReview = false;
    }

    @Column(nullable = false)
    private String status; // ACTIVE, CONSUMED, EXPIRED, DISCARDED

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

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

    public void addQuantity(Double additionalQuantity) {
        if(additionalQuantity != null) {
            this.remainingQuantity = (this.remainingQuantity != null ? this.remainingQuantity : 0.0) + additionalQuantity;
        }
    }

    public void updateInventoryInfo(UnitType unitType, Double remainingQuantity, String customQuantityText,
                                    String unit, LocalDate consumeByDate, String locationCategory) {

        if(consumeByDate != null) this.consumeByDate = consumeByDate;
        if(locationCategory != null) this.locationCategory = locationCategory;

        if(unitType != null) {
            this.unitType = unitType;

            if(unitType == UnitType.CUSTOM) {
                this.unit = null;
                this.remainingQuantity = null;
            } else if(unitType == UnitType.PERCENT || unitType == UnitType.QUANTITY) {
                this.customQuantityText = null;
            }
        }
        if(this.unitType == UnitType.CUSTOM) {
            if(customQuantityText != null) {
                this.customQuantityText = customQuantityText;
            }
            if(this.customQuantityText == null || this.customQuantityText.isBlank() || "0".equals(this.customQuantityText)) {
                this.status = "CONSUMED";
            } else if("CONSUMED".equals(this.status)) {
                this.status = "ACTIVE";
            }
        } else {
            if(remainingQuantity != null) {
                this.remainingQuantity = Math.max(0.0, remainingQuantity);
            }
            if(unit != null) {
                this.unit = unit;
            }
            if(this.remainingQuantity == 0.0) {
                this.status = "CONSUMED";
            } else if(this.remainingQuantity > 0.0 && "CONSUMED".equals(this.status)) {
                this.status = "ACTIVE";
            }
        }
    }

    // 재고 차감 및 자동 소진 처리
    public void closeAs(String reason) {
        this.status = reason;
        if (this.unitType == UnitType.CUSTOM) this.customQuantityText = "0";
        else this.remainingQuantity = 0.0;
    }

    public void consumeQuantity(Double amount, String customText) {
        if(this.unitType == UnitType.CUSTOM) {
            // CUSTOM인 경우 텍스트 업데이트
            this.customQuantityText = customText;
            if(customText == null || customText.isBlank() || "0".equals(customText)) {
                this.status = "CONSUMED";
            }
        } else if(amount != null && amount > 0) {
            // QUANTITY 또는 PERCENT인 경우 숫자 차감
            var used = com.example.fridiet.service.QuantityPolicy.decimal(amount);
            var current = com.example.fridiet.service.QuantityPolicy.decimal(this.remainingQuantity);
            if (used.signum() <= 0 || used.compareTo(current) > 0) throw com.example.fridiet.service.QuantityPolicy.invalid("잔량 이내의 사용량이 필요합니다.");
            this.remainingQuantity = current.subtract(used).doubleValue();
            if(this.remainingQuantity == 0.0) {
                this.status = "CONSUMED";
            }
        }
    }
    
    // 재고 원상복귀
    public void restoreQuantity(Double quantityUsed, String preCustomQuantityText) {
        if(this.unitType == UnitType.CUSTOM) {
            this.customQuantityText = preCustomQuantityText;
        } else {
            if(quantityUsed != null && quantityUsed > 0) {
                if(this.remainingQuantity != null) {
                    this.remainingQuantity = com.example.fridiet.service.QuantityPolicy.decimal(this.remainingQuantity)
                            .add(com.example.fridiet.service.QuantityPolicy.decimal(quantityUsed)).doubleValue();
                }
            }
        }

        if("CONSUMED".equals(this.status)) {
            this.status = "ACTIVE";
            checkAndSetExpired(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
        }
    }

    // 소비기한이 지났으면 status를 EXPIRED로 변경
    public boolean checkAndSetExpired(LocalDate today) {
        // 이미 EXPIRED인 경우는 변경 제외
        if(!"ACTIVE".equals(this.status)) {
            return false;
        }

        if(this.consumeByDate != null && this.consumeByDate.isBefore(today)) {
            this.status = "EXPIRED";
            return true;
        }
        return false;
    }

    public void delete() {
        this.status = "DISCARDED";
    }
}
