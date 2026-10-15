package com.example.fridiet.dto;

import com.example.fridiet.entity.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class CartCreateRequest {

    @Getter
    @NoArgsConstructor
    public static class CreateCart {
        @NotNull(message = "냉장고 ID는 필수입니다.")
        private Long fridgeId;

        @NotBlank(message = "장바구니 이름은 필수입니다.")
        private String name; // 예: "주말 장보기 목록"
    }

    @Getter
    @NoArgsConstructor
    public static class AddItem {
        @NotBlank(message = "품목 이름은 필수입니다.")
        private String itemName; // 예: "우유", "양파"

        private UnitType unitType; // QUANTITY(기본값), PERCENT, CUSTOM

        // 1) QUANTITY 또는 PERCENT인 경우
        private Double quantity;
        private String unit;

        // 2) CUSTOM인 경우
        private String customQuantityText;

        private String memo;

        private String sourceType; // MANUAL, RECIPE, LOW_STOCK, EXPIRY
        private Long sourceRefId; // 레시피 ID 등 출처 참조 PK
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateItemState {
        @NotBlank(message = "변경할 상태값은 필수입니다.")
        private String state; // NEEDED, BOUGHT, REMOVED
    }
}
