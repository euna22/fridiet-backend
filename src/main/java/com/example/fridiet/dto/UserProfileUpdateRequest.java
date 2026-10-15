package com.example.fridiet.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserProfileUpdateRequest {

    private String healthCondition;
    private String healthGoal;
}
