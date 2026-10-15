package com.example.fridiet.controller;

import com.example.fridiet.dto.*;
import com.example.fridiet.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 내 FCM 디바이스 토큰 등록/갱신 API
    @PostMapping("/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FcmTokenRequest request) {

        userService.updateFcmToken(userId, request.getFcmToken());
        return ResponseEntity.ok().build();
    }

    /**
     * 소셜 계정 연동
     */
    @PostMapping("/me/social-link")
    public ResponseEntity<?> linkSocialAccount(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SocialLinkRequest request) {
        try {
            userService.linkSocialAccount(userId, request);
            return ResponseEntity.ok("소셜 계정이 성공적으로 연동되었습니다.");
        } catch(Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 비밀번호 변경
     */
    @PostMapping("/password/verify")
    public ResponseEntity<String> updatePassword(@Valid @RequestBody PasswordVerifyRequest request) {
        // 이름과 이메일만으로는 계정 소유를 증명할 수 없습니다.
        return ResponseEntity.status(503).body("안전한 본인 확인 절차 준비 중으로 비밀번호 찾기를 사용할 수 없습니다.");
    }

    /**
     * 비밀번호 변경
     */
    @PostMapping("/password/reset")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        try {
            userService.resetPasswordWithToken(request.getResetToken(), request.getNewPassword());
            return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * DELETE /api/users/me
     * 회원 탈퇴
     */
    @DeleteMapping("/me")
    public ResponseEntity<?> withdrawAccount(@AuthenticationPrincipal Long userId) {
        try {
            userService.deleteAccount(userId);
            return ResponseEntity.ok("회원 탈퇴 처리에 성공하였습니다.");
        } catch(Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 프로필 수정
     * PATCH /api/users/me/profile
     */
    @PatchMapping("/me/profile")
    public ResponseEntity<String> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        try {
            userService.updateProfile(userId, request);
            return ResponseEntity.ok("프로필 정보가 성공적으로 변경되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("프로필 수정 중 오류가 발생했습니다.");
        }
    }

    /**
     * 알림 설정 조회
     */
    @GetMapping("/me/notification-settings")
    public ResponseEntity<NotificationSettingsResponse> getNotificationSettings(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getNotificationSettings(userId));
    }
    /**
     * 알림 설정 수정
     */
    @PatchMapping("/me/notification-settings")
    public ResponseEntity<Void> updateNotificationSettings(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid NotificationSettingsRequest request) {

        userService.updateNotificationSettings(userId, request);

        return ResponseEntity.ok().build();
    }

}
