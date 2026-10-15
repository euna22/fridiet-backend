package com.example.fridiet.dto;

import com.example.fridiet.entity.UnitType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class BuyRequest extends QuantityMetadata {

    // 소비기한을 입력 받으면 사용, 없으면 기본값 세팅
    private LocalDate expirationDate;

    //REFRIGERATED(기본값), FROZEN, ROOM_TEMP
    private String locationCategory;

    // 단위 입력 유형(null인 경우 QUANTITY 적용)
    private UnitType unitType;

    // 1) QUANTITY 또는 PERCENT인 경우
    private Double quantity;
    private String unit;

    // 2) CUSTOM인 경우
    private String customQuantityText;
}
