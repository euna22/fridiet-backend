package com.example.fridiet.controller;

import com.example.fridiet.entity.User;
import com.example.fridiet.service.OAuthService;
import com.example.fridiet.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;
    private final JwtUtil jwtUtil;

    @Value("${frontend.redirect-uri:http://localhost:8081/login}")
    private String frontendRedirectUri;

    @GetMapping("/login/oauth2/code/kakao")
    public ResponseEntity<Void> kakaoLogin(@RequestParam("code") String code) {

        User loginUser = oAuthService.processKakaoLogin(code);
        String token = jwtUtil.createToken(loginUser.getId(), loginUser.getEmail());

        return redirectToFrontend(token);
    }

    @GetMapping("/login/oauth2/code/google")
    public ResponseEntity<Void> googleLogin(@RequestParam("code") String code) {

        User loginUser = oAuthService.processGoogleLogin(code);
        String token = jwtUtil.createToken(loginUser.getId(), loginUser.getEmail());

        return redirectToFrontend(token);
    }

    private ResponseEntity<Void> redirectToFrontend(String token) {
        URI location = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("token", token)
                .build()
                .encode()
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .build();
    }

    @GetMapping("/link/oauth2/code/kakao")
    public ResponseEntity<Void> kakaoLinkCallback(@RequestParam("code") String code) {
        URI location = UriComponentsBuilder.fromUriString("fridiet://oauth-callback")
                .queryParam("code", code)
                .queryParam("provider", "KAKAO")
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/link/oauth2/code/google")
    public ResponseEntity<Void> googleLinkCallback(@RequestParam("code") String code) {
        URI location = UriComponentsBuilder.fromUriString("fridiet://oauth-callback")
                .queryParam("code", code)
                .queryParam("provider", "GOOGLE")
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
