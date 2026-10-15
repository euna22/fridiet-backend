package com.example.fridiet.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FridgeSummaryResponse {

    private Long fridgeId;
    private String fridgeName;
    private Boolean isMain; // true: 즐겨찾기된 대표 냉장고

    public static FridgeSummaryResponse of(Long fridgeId, String fridgeName, boolean isMain) {
        return FridgeSummaryResponse.builder()
                .fridgeId(fridgeId)
                .fridgeName(fridgeName)
                .isMain(isMain)
                .build();
    }
}