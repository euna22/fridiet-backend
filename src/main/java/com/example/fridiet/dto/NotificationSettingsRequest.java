package com.example.fridiet.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsRequest {

    private Boolean pushEnabled;
    private Boolean expiryEnabled;
    private Boolean fridgeActivityEnabled;

    @Pattern(regexp = "^(09:00|13:00|19:00)$", message = "알림 시간은 09:00, 13:00, 19:00 중 하나여야 합니다.")
    private String notificationTime;
}
