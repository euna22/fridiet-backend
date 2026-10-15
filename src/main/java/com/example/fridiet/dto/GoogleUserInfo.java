package com.example.fridiet.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoogleUserInfo {

    private String id; // socialId
    private String email;
    private String name;
    private String picture; // 프로필 사진 URL
}
