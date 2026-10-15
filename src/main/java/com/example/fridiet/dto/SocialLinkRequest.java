package com.example.fridiet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SocialLinkRequest {

    @NotBlank(message = "소셜 제공자(KAKAO, GOOGLE)는 필쉬입니다.")
    private String provider;

    @NotBlank(message = "소셜 인가 코드 또는 Access Token이 필요합니다.")
    private String tokenOrCode;
}
