package com.example.fridiet.controller;

import com.example.fridiet.dto.EmailSendRequest;
import com.example.fridiet.dto.EmailVerifyRequest;
import com.example.fridiet.service.EmailAuthService;
import com.example.fridiet.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailAuthController {

    private final EmailAuthService emailAuthService;
    private final UserService userService;

    /**
     * POST /api/auth/email/send/signup
     * 인증 코드 발송 요청
     */
    @PostMapping("/send/signup")
    public ResponseEntity<Map<String, String>> sendSignupCode(@Valid @RequestBody EmailSendRequest request) {
        emailAuthService.sendSignupCode(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "인증 코드가 전송되었습니다. 이메일을 확인해주세요."));
    }

    /**
     * POST /api/auth/email/send/reset-password
     * 인증 코드 발송 요청
     */
    @PostMapping("/send/reset-password")
    public ResponseEntity<Map<String, String>> sendPasswordResetCode(@Valid @RequestBody EmailSendRequest request) {
        emailAuthService.sendPasswordResetCode(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "비밀번호 재설정 코드가 전송되었습니다. 이메일을 확인해주세요."));
    }

    /**
     * POST /api/auth/email/verify
     * 인증 코드 검증
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyCode(@Valid @RequestBody EmailVerifyRequest request) {
        boolean isSuccess = emailAuthService.verifyCode(request.getEmail(), request.getCode());
        if(!isSuccess) {
            throw new IllegalStateException("인증번호가 일치하지 않거나 만료되었습니다.");
        }

        String resetToken = userService.issueResetToken(request.getEmail());

        return ResponseEntity.ok(Map.of("resetToken", resetToken));
    }
}
