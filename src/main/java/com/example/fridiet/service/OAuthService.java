package com.example.fridiet.service;

import com.example.fridiet.entity.Fridge;
import com.example.fridiet.entity.User;
import com.example.fridiet.dto.KakaoUserInfo;
import com.example.fridiet.dto.GoogleUserInfo;
import com.example.fridiet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final UserRepository userRepository;
    private final FridgeService fridgeService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @Value("${kakao.link-redirect-uri}")
    private String kakaoLinkRedirectUri;

    @Value("${kakao.admin-key}")
    private String kakaoAdminKey;

    @Transactional
    public User processKakaoLogin(String code) {
        // 프론트가 준 인가 코드로 카카오 액세스 토큰 받아오기
        String accessToken = getKakaoAccessToken(code, kakaoRedirectUri);

        // 받아온 액세스 토큰으로 카카오 유저 정보 요청
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<String> request = new HttpEntity<>(headers);
        String kakaoUserApiUrl = "https://kapi.kakao.com/v2/user/me";

        ResponseEntity<KakaoUserInfo> response = restTemplate.exchange(
                kakaoUserApiUrl, HttpMethod.GET, request, KakaoUserInfo.class
        );

        KakaoUserInfo kakaoUserInfo = response.getBody();
        if (kakaoUserInfo == null) {
            throw new RuntimeException("카카오 유저 정보를 불러오는데 실패했습니다.");
        }

        String socialId = String.valueOf(kakaoUserInfo.getId());
        String name = kakaoUserInfo.getKakao_account().getProfile().getNickname();
        String email = kakaoUserInfo.getKakao_account().getEmail();

        return processSocialLogin("KAKAO", socialId, email, name);
    }

    // 인가 코드로 토큰을 구하는 메서드
    private String getKakaoAccessToken(String code, String redirectUri) {
        String tokenUrl = "https://kauth.kakao.com/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", code); // 프론트가 넘겨준 인가 코드

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        Map<String, Object> responseBody = response.getBody();

        if (responseBody == null || !responseBody.containsKey("access_token")) {
            throw new RuntimeException("카카오 액세스 토큰을 발급받지 못했습니다.");
        }

        return (String) responseBody.get("access_token");
    }

    // 소셜 연동 시 카카오 socialId 가져오기
    public String getKakaoSocialId(String code) {
        String accessToken = getKakaoAccessToken(code, kakaoLinkRedirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<String> request = new HttpEntity<>(headers);
        String kakaoUserApiUrl = "https://kapi.kakao.com/v2/user/me";

        ResponseEntity<KakaoUserInfo> response = restTemplate.exchange(
                kakaoUserApiUrl, HttpMethod.GET, request, KakaoUserInfo.class
        );

        KakaoUserInfo kakaoUserInfo = response.getBody();
        if (kakaoUserInfo == null) {
            throw new RuntimeException("카카오 유저 정보를 불러오는데 실패했습니다.");
        }

        return String.valueOf(kakaoUserInfo.getId());
    }

    // 카카오 소셜 연동 해제
    public void unlinkKakao(String providerId) {
        String url = "https://kapi.kakao.com/v1/user/unlink";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "kakaoAK " + kakaoAdminKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("target_id_type", "user_id");
        body.add("target_id", providerId);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        } catch(Exception e) {
            System.err.println("카카오 연동 해제 실패: " + e.getMessage());
        }
    }

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.client-secret}")
    private String googleClientSecret;

    @Value("${google.redirect-uri}")
    private String googleRedirectUri;

    @Value("${google.link-redirect-uri}")
    private String googleLinkRedirectUri;

    @Transactional
    public User processGoogleLogin(String code) {
        // 인가 코드로 구글 액세스 토큰 받아오기
        String accessToken = getGoogleAccessToken(code, googleRedirectUri);

        // 받아온 액세스 토큰으로 구글 유저 정보 요청
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        HttpEntity<String> request = new HttpEntity<>(headers);
        String googleUserApiUrl = "https://www.googleapis.com/oauth2/v2/userinfo";

        try {
            ResponseEntity<GoogleUserInfo> response = restTemplate.exchange(
                    googleUserApiUrl, HttpMethod.GET, request, GoogleUserInfo.class
            );

            GoogleUserInfo googleUserInfo = response.getBody();
            if (googleUserInfo == null) {
                throw new RuntimeException("구글 유저 정보를 불러오는데 실패했습니다.");
            }

            String socialId = googleUserInfo.getId();
            String name = googleUserInfo.getName();
            String email = googleUserInfo.getEmail();

            return processSocialLogin("GOOGLE", socialId, email, name);

        } catch (Exception e) {
            throw new RuntimeException("구글 로그인 중 에러가 발생했습니다: " + e.getMessage());
        }
    }

    // 구글 인가 코드로 토큰을 구하는 메서드
    private String getGoogleAccessToken(String code, String redirectUri) {
        String tokenUrl = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code); // 프론트가 넘겨준 인가 코드

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        Map<String, Object> responseBody = response.getBody();

        if (responseBody == null || !responseBody.containsKey("access_token")) {
            throw new RuntimeException("구글 액세스 토큰을 발급받지 못했습니다.");
        }

        return (String) responseBody.get("access_token");
    }

    // 소셜 연동 시 카카오 socialId 가져오기
    public String getGoogleProviderId(String code) {
        String accessToken = getGoogleAccessToken(code, googleLinkRedirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        HttpEntity<String> request = new HttpEntity<>(headers);
        String googleUserApiUrl = "https://www.googleapis.com/oauth2/v2/userinfo";

        ResponseEntity<GoogleUserInfo> response = restTemplate.exchange(
                googleUserApiUrl, HttpMethod.GET, request, GoogleUserInfo.class
        );

        GoogleUserInfo googleUserInfo = response.getBody();
        if (googleUserInfo == null) {
            throw new RuntimeException("구글 유저 정보를 불러오는데 실패했습니다.");
        }

        return googleUserInfo.getId();
    }

    // 구글 소셜 연동 해제
    public void unlinkGoogle(String accessToken) {

        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        String url = "https://oauth2.googleapis.com/revoke";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", accessToken);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            restTemplate.postForEntity(url, requestEntity, String.class);
        } catch(Exception e) {
            System.err.println("구글 연동 해제 실패: " + e.getMessage());
        }
    }

    // 통합  소셜 로그인 및 미에일 자동 연동
    @Transactional
    public User processSocialLogin(String provider, String socialId, String email, String name) {
        // 탈퇴하지 않은(isDeleted=false) 기존 유저가 있는지 확인
        return userRepository.findBySocialIdAndIsDeletedFalse(socialId)
                .orElseGet(() -> {
                    // 동일 이메일로 가입한 회원이 있는지 확인, 자동 연동
                    if(email != null && !email.isBlank()) {
                        User existingUser = userRepository.findByEmail(email).orElse(null);
                        if(existingUser != null) {
                            // 일반 회원에 소셜 로그인 정보 업데이트(연동)
                            existingUser.updateSocialInfo(socialId, provider);
                            return userRepository.save(existingUser);
                        }
                    }
                    // 기존 유저가 없으면 신규 소셜 회원 생성
                    User newUser = User.builder()
                            .socialId(socialId)
                            .socialProvider(provider)
                            .name(name != null ? name : "User")
                            .email(email)
                            .isDeleted(false)
                            .build();

                    User savedUser = userRepository.save(newUser);

                    Fridge defaultFridge = fridgeService.createDefaultFridge(savedUser);

                    savedUser.updateMainFridge(defaultFridge.getId());

                    return savedUser;
                });
    }
}