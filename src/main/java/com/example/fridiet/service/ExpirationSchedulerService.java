package com.example.fridiet.service;

import com.example.fridiet.entity.InventoryBatch;
import com.example.fridiet.repository.InventoryBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpirationSchedulerService {

    private final InventoryBatchRepository inventoryBatchRepository;

    /**
     * 테스트용, 서버가 부팅될 때마다 소비기한 만료 확인
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        log.info("[서버 시작] 소비기한 만료 재고 즉시 체크 중...");
        updateExpiredInventoryStatus();
    }

    /**
     * 매일 자정 소비기한이 만료된 식재료의 status를 'EXPIRED'로 변경
     * (cron = "초 분 시 일 월 요일")
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul") // 매일 00:00:00 실행
    @Transactional
    public void updateExpiredInventoryStatus() {
        LocalDate today = LocalDate.now();

        // 현재 ACTIVE 상태인 재고들만 조회
        List<InventoryBatch> activeBatches = inventoryBatchRepository.findAllByStatus("ACTIVE");

        int expiredCount = 0;
        for (InventoryBatch batch : activeBatches) {
            boolean updated = batch.checkAndSetExpired(today);
            if (updated) {
                expiredCount++;
            }
        }

        log.info("소비기한 만료 상태 업데이트 완료: 총 {}개 항목이 EXPIRED로 변경되었습니다.", expiredCount);
    }
}