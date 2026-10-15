package com.example.fridiet.service;

import com.example.fridiet.dto.NotificationSettingsRequest;
import com.example.fridiet.dto.NotificationSettingsResponse;
import com.example.fridiet.dto.SocialLinkRequest;
import com.example.fridiet.dto.UserProfileUpdateRequest;
import com.example.fridiet.entity.User;
import com.example.fridiet.repository.UserRepository;
import com.example.fridiet.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OAuthService oAuthService;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;
    private static final String RESET_TOKEN_PREFIX = "RESET_TOKEN:";
    private static final long RESET_TOKEN_EXPIRE_SECONDS = 900L;


    // 사용자의 FCM 디바이스 토큰 저장 및 갱신
    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));

        user.updateFcmToken(fcmToken);
    }

    /**
     * 일반 계정에 소셜 로그인 계정 연동
     */
    @Transactional
    public void linkSocialAccount(Long userId, SocialLinkRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));

        if(user.getSocialId() != null && !user.getSocialId().isBlank()) {
            throw new IllegalStateException("이미 " + user.getSocialProvider() + "계정과 연동되어 있습니다.");
        }

        String provider = request.getProvider().toUpperCase();
        String socialId = "";

        if("KAKAO".equals(provider)) {
            socialId = oAuthService.getKakaoSocialId(request.getTokenOrCode());
        } else if("GOOGLE".equals(provider)) {
            socialId = oAuthService.getGoogleProviderId(request.getTokenOrCode());
        } else {
            throw new IllegalArgumentException("지원하지 않는 소셜 플랫폼입니다: " + provider);
        }

        boolean isAlreadyLinked = userRepository.existsBySocialProviderAndSocialId(provider, socialId);
        if(isAlreadyLinked) {
            throw new IllegalStateException("해당 " + provider + "계정은 이미 연동되어 있습니다.");
        }

        user.linkSocial(provider, socialId);
    }

    /**
     * 비밀번호 변경
     */
    @Transactional
    public void resetPasswordWithToken(String resetToken, String newPassword) {
        if (resetToken == null || resetToken.isBlank()) {
            throw new IllegalArgumentException("인증 토큰이 필요합니다.");
        }
        String email = redisUtil.consumeData(RESET_TOKEN_PREFIX + resetToken);

        if (email == null) {
            throw new IllegalArgumentException("만료되었거나 유효하지 않은 인증 토큰입니다. 다시 시도해 주세요.");
        }

        // 새 비밀번호 암호화 후 반영
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if ("KAKAO".equals(user.getSocialProvider()) || "GOOGLE".equals(user.getSocialProvider())) {
            throw new IllegalStateException("소셜 로그인 계정은 비밀번호를 재설정할 수 없습니다.");
        }

        user.updatePassword(passwordEncoder.encode(newPassword));

        log.info("비밀번호 재설정 완료: email={}", email);
    }

    /**
     * 이메일 인증 후 재설정 토큰 발급
     */
    public String issueResetToken(String email) {
        String resetToken = UUID.randomUUID().toString();
        redisUtil.setDataExpire(RESET_TOKEN_PREFIX + resetToken, email, RESET_TOKEN_EXPIRE_SECONDS);
        return resetToken;
    }

    /**
     * 회원 탈퇴 처리(재가입 허용)
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if(Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new IllegalStateException("이미 탈퇴 처리된 계정입니다.");
        }else if("KAKAO".equalsIgnoreCase(user.getSocialProvider()) && user.getSocialId() != null) {
            oAuthService.unlinkKakao(user.getSocialId());
        } else if("GOOGLE".equalsIgnoreCase(user.getSocialProvider()) && user.getSocialId() != null) {
            oAuthService.unlinkGoogle(user.getSocialId());
        }

        // 동일 이메일/소셜 계정 재가입 허용을 위한 익명화 처리
        String anonymizedEmail = "deleted_" + userId + "_" + System.currentTimeMillis() + "@deleted.com";

        // 회원 탈퇴 상태로 변경
        user.withdraw(anonymizedEmail);
    }

    /**
     * 프로필 수정
     */
    @Transactional
    public void updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new IllegalStateException("탈퇴한 계정의 정보는 수정할 수 없습니다.");
        }

        user.updateProfile(request.getHealthCondition(), request.getHealthGoal());
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse getNotificationSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        return NotificationSettingsResponse.builder()
                .pushEnabled(user.getPushEnabled() != null ? user.getPushEnabled() : true)
                .expiryEnabled(user.getExpiryEnabled() != null ? user.getExpiryEnabled() : true)
                .fridgeActivityEnabled(user.getFridgeActivityEnabled() != null ? user.getFridgeActivityEnabled() : true)
                .notificationTime(user.getNotificationTime() != null ? user.getNotificationTime() : "09:00")
                .build();
    }

    @Transactional
    public void updateNotificationSettings(Long userId, NotificationSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        user.updateNotificationSettings(
                request.getPushEnabled(),
                request.getExpiryEnabled(),
                request.getFridgeActivityEnabled(),
                request.getNotificationTime()
        );
    }
}
