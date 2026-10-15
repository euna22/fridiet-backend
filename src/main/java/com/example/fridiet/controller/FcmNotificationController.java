package com.example.fridiet.controller;

import com.example.fridiet.dto.NotificationResponseDto;
import com.example.fridiet.service.FcmNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class FcmNotificationController {

    private final FcmNotificationService fcmNotificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponseDto>> getMyNotifications(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(fcmNotificationService.getUserNotifications(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {

        fcmNotificationService.markAsRead(id);

        return ResponseEntity.ok().build();
    }
}
