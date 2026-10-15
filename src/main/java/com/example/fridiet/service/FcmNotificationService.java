package com.example.fridiet.service;

import com.example.fridiet.dto.NotificationResponseDto;
import com.example.fridiet.entity.FridgeMember;
import com.example.fridiet.entity.FcmNotification;
import com.example.fridiet.entity.User;
import com.example.fridiet.repository.FridgeMemberRepository;
import com.example.fridiet.repository.FcmNotificationRepository;
import com.example.fridiet.repository.UserRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcmNotificationService {

    private final FridgeMemberRepository fridgeMemberRepository;
    private final FcmNotificationRepository fcmNotificationRepository;
    private final UserRepository userRepository;

    public void sendNotificationToFridgeMembers(Long fridgeId, Long excludeUserId, String title, String body) {

        List<FridgeMember> members = fridgeMemberRepository.findByFridgeIdWithUser(fridgeId);

        List<String> targetTokens = members.stream()
                .map(FridgeMember::getUser)
                .filter(Objects::nonNull)
                .filter(user -> excludeUserId == null || !user.getId().equals(excludeUserId))
                .filter(user -> !Boolean.FALSE.equals(user.getPushEnabled()))
                .filter(user -> !Boolean.FALSE.equals(user.getFridgeActivityEnabled()))
                .map(User::getFcmToken)
                .distinct()
                .collect(Collectors.toList());

        if (targetTokens.isEmpty()) {
            log.info("냉장고(ID: {})에 발송할 FCM 토큰이 없습니다.", fridgeId);
            return;
        }

        // 안드로이드 팝업용 설정
        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setChannelId("default")
                        .setDefaultSound(true)
                        .setDefaultVibrateTimings(true)
                        .build())
                .build();

        // MulticastMessage 메시지 생성
        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setAndroidConfig(androidConfig)
                .putData("fridgeId", String.valueOf(fridgeId))
                .putData("type", "fridge")
                .addAllTokens(targetTokens)
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("FCM 알림 발송 성공 - 냉장고 ID: {}, 성공: {}건, 실패: {}건",
                    fridgeId, response.getSuccessCount(), response.getFailureCount());
        } catch (Exception e) {
            log.error("FCM 알림 발송 실패 - 냉장고 ID: {}", fridgeId, e);
        }
    }

    public void sendNotificationToUser(Long userId, Long fridgeId, String title, String body) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        String targetToken = user.getFcmToken();

        if (targetToken == null || targetToken.trim().isEmpty()) {
            log.info("FCM 토큰이 없습니다.");
            return;
        }

        // 안드로이드 팝업용 설정
        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setChannelId("default")
                        .setDefaultSound(true)
                        .setDefaultVibrateTimings(true)
                        .build())
                .build();

        Message message = Message.builder()
                .setToken(targetToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setAndroidConfig(androidConfig)
                .putData("fridgeId", String.valueOf(fridgeId))
                .putData("type", "expiry")
                .build();

        try {
            FirebaseMessaging.getInstance().send(message);
            log.info("FCM 알림 발송 성공");
        } catch (Exception e) {
            log.error("FCM 알림 발송 실패", e);
        }
    }

    public List<NotificationResponseDto> getUserNotifications(Long userId) {
        List<FcmNotification> fcmNotifications = fcmNotificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        return fcmNotifications.stream()
                .map(n -> NotificationResponseDto.from(n, today, yesterday))
                .toList();
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        FcmNotification fcmNotification = fcmNotificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 알림이 존재하지 않습니다."));

        fcmNotification.markAsRead();
    }

    // 시간 표시
    public String formatTime(LocalDateTime createdAt, LocalDate today, LocalDate yesterday) {
        LocalDate createdDate = createdAt.toLocalDate();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(" a h:mm", Locale.KOREAN);

        if(createdDate.equals(today)) {
            long minutesDiff = ChronoUnit.MINUTES.between(createdAt, now);

            if(minutesDiff < 1) return "방금 전";
            if(minutesDiff < 60) return minutesDiff + "분 전";

            return createdAt.format(timeFormatter);
        } else if(createdDate.equals(yesterday)) {
            return "어제 " + createdAt.format(timeFormatter);
        } else {
            return createdAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        }
    }
}