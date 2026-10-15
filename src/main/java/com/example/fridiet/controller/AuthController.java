package com.example.fridiet.controller;

import com.example.fridiet.dto.LoginRequest;
import com.example.fridiet.dto.LoginResponse;
import com.example.fridiet.dto.RegisterRequest;
import com.example.fridiet.entity.User;
import com.example.fridiet.service.AuthService;
import com.example.fridiet.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    // 일반 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody RegisterRequest request) {
        try {
            User registerUser = authService.register(request);
            return ResponseEntity.ok(registerUser);
        } catch(Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 일반 로그인 API
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            User loginUser = authService.login(request);
            String token = jwtUtil.createToken(loginUser.getId(), loginUser.getEmail());
            return ResponseEntity.ok(new LoginResponse(token, loginUser, loginUser.getMainFridgeId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 일반 로그아웃 API
    @PostMapping("/logout")
    public ResponseEntity<String> logout() {

        return ResponseEntity.ok("로그아웃 되었습니다.");
    }
}
