package com.example.fridiet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "container")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Container {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "container_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    // 용기에 부착된 고유 QR 코드
    @Column(name = "qr_code_token", nullable = false, unique = true)
    private String qrCodeToken;

    private String name; // 용기 별칭 (예: "유리 반찬통 A", "굴소스 병")

    // 현재 이 용기에 담겨있는 재고 (비어있으면 null)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_batch_id")
    private InventoryBatch currentBatch;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 용기 내용물(재고) 연결/교체/비우기 도메인 메서드
    public void linkInventoryBatch(InventoryBatch batch) {
        this.currentBatch = batch;
    }

    public void clearContainer() {
        this.currentBatch = null;
    }
}