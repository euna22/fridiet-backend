package com.example.fridiet.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoUserInfo {

    private Long id; // socialId 값
    private KakaoAccount kakao_account;

    @Getter
    @NoArgsConstructor
    public static class KakaoAccount {
        private Profile profile;
        private String email;
    }

    @Getter
    @NoArgsConstructor
    public static class Profile {
        private String nickname;
    }
}
