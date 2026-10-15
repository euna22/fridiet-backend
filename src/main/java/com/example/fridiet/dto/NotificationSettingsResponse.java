package com.example.fridiet.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationSettingsResponse {
    private Boolean pushEnabled;
    private Boolean expiryEnabled;
    private Boolean fridgeActivityEnabled;
    private String notificationTime;
}
