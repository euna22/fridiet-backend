package com.example.fridiet.service;

import com.example.fridiet.entity.*;
import com.example.fridiet.repository.FridgeMemberRepository;
import com.example.fridiet.repository.InventoryBatchRepository;
import com.example.fridiet.repository.FcmNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpirationNotificationScheduler {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final FcmNotificationService fcmNotificationService; // FCM 발송 전용 서비스
    private final FridgeMemberRepository fridgeMemberRepository;
    private final FcmNotificationRepository fcmNotificationRepository;

    // 매일 오전 9시에 자동으로 실행 (cron = "초 분 시 일 월 요일")
    @Scheduled(cron = "0 0 9,13,19 * * *", zone = "Asia/Seoul")
    public void sendExpirationNotifications() {

        int hour = LocalTime.now(ZoneId.of("Asia/Seoul")).getHour();
        String currentSlot = String.format("%02d:00", hour);

        LocalDate today = LocalDate.now();
        LocalDate targetDate = LocalDate.now().plusDays(3);
        List<InventoryBatch> expiringBatches = inventoryBatchRepository.findBatchesExpiringOn(today, targetDate);

        if(expiringBatches.isEmpty()) {
            log.info("소비기한이 3일 이내인 식재료가 없습니다.");
            return;
        }

        Map<Fridge, List<InventoryBatch>> todayExpiringByFridge = expiringBatches.stream()
                .filter(batch -> batch.getConsumeByDate() != null && batch.getConsumeByDate().isEqual(today))
                .filter(batch -> batch.getFridge() != null)
                .collect(Collectors.groupingBy(InventoryBatch::getFridge));

        for(Map.Entry<Fridge, List<InventoryBatch>> entry : todayExpiringByFridge.entrySet()) {
            Fridge fridge = entry.getKey();
            List<InventoryBatch> batches = entry.getValue();

            List<FridgeMember> allMembers = fridgeMemberRepository.findByFridgeIdWithUser(fridge.getId());
            List<FridgeMember> targetMembers = allMembers.stream()
                    .filter(member -> {
                        User user = member.getUser();
                        if(user == null) return false;

                        if(Boolean.FALSE.equals(user.getPushEnabled())) return false;
                        if(Boolean.FALSE.equals(user.getExpiryEnabled())) return false;

                        String userSlot = user.getNotificationTime() != null ? user.getNotificationTime() : "09:00";
                        return currentSlot.equals(userSlot);
                    }).toList();
            if(targetMembers.isEmpty()) continue;

            List<String> ingredientNames = batches.stream()
                    .map(b -> b.getIngredientMaster() != null ? b.getIngredientMaster().getCanonicalName() : b.getIngredientName())
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            if(ingredientNames.isEmpty()) {
                continue;
            }

            String joinedIngredientNames = String.join(", ", ingredientNames);

            String title;
            String body = "오늘 안에 소비해 주세요.";

            if(ingredientNames.size() == 1) {
                title = String.format("%s의 소비기한이 오늘까지예요", ingredientNames.get(0));
            } else if(ingredientNames.size() == 2) {
                title = String.format("%s와(과) %s의 소비기한이 오늘까지예요.", ingredientNames.get(0), ingredientNames.get(1));
            } else {
                title = String.format("%s, %s 외 %d개의 소비기한이 오늘까지예요.", ingredientNames.get(0), ingredientNames.get(1), ingredientNames.size() - 2);
            }

            for(FridgeMember member : targetMembers) {
                User targetUser = member.getUser();
                if(targetUser == null) continue;

                fcmNotificationService.sendNotificationToUser(targetUser.getId(), fridge.getId(), title, body);

                fcmNotificationRepository.save(FcmNotification.builder()
                        .userId(member.getUser().getId())
                        .type("expiry")
                        .title(title)
                        .message(body)
                        .ingredientId(batches.get(0).getId())
                                .ingredientNameText(joinedIngredientNames)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }
    }
}