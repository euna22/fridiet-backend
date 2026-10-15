package com.example.fridiet.dto;

import com.example.fridiet.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String email;
    private String name;
    private Long mainFridgeId;
    private String healthCondition;
    private String healthGoal;
    private String socialId;
    private String socialProvider;

    public LoginResponse(String token, User user, Long mainFridgeId) {
        this.token = token;
        this.userId = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.mainFridgeId = (mainFridgeId != null) ? mainFridgeId : user.getMainFridgeId();
        this.healthCondition = user.getHealthCondition();
        this.healthGoal = user.getHealthGoal();
        this.socialId = (user.getSocialId() != null) ? user.getSocialId() : null;
        this.socialProvider = user.getSocialProvider();
    }
}
