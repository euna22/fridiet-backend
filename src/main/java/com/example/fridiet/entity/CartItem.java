package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cart_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private IngredientMaster ingredientMaster; //nullable

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type")
    private UnitType unitType; // QUANTITY, PERCENT, CUSTOM

    private Double quantity;

    @Column(name = "custom_quantity_text")
    private String customQuantityText;

    private String unit;
    private String state;
    private String memo;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "purchased_batch_id")
    private Long purchasedBatchId;

    public void completePurchase(Long batchId, User buyer) {
        this.purchasedBatchId = batchId;
        this.boughtBy = buyer;
        this.state = "REMOVED";
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bought_by")
    private User boughtBy;

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
    public CartItem(Cart cart, IngredientMaster ingredientMaster, String itemName,
                    UnitType unitType, Double quantity, String customQuantityText, String unit,
                    String state, String memo, String sourceType, Long sourceRefId, User boughtBy, User createdBy) {
        this.cart = cart;
        this.ingredientMaster = ingredientMaster;
        this.itemName = itemName;
        this.unitType = (unitType != null) ? unitType : UnitType.QUANTITY;
        this.quantity = quantity;
        this.customQuantityText = customQuantityText;
        this.unit = unit;
        this.state = (state != null) ? state : "NEEDED";
        this.memo = memo;
        this.sourceType = (sourceType != null) ? sourceType : "MANUAL";
        this.sourceRefId = sourceRefId;
        this.boughtBy = boughtBy;
        this.createdBy = createdBy;
    }

    // 💡 상태 변경 및 구매자 세팅 도메인 메서드
    public void updateState(String newState, User updatedBy) {
        this.state = newState;
        if ("BOUGHT".equals(newState)) {
            this.boughtBy = updatedBy;
        } else if ("NEEDED".equals(newState)) {
            this.boughtBy = null; // 구매 취소 시 초기화
        }
    }
}
