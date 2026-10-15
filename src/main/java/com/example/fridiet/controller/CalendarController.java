package com.example.fridiet.controller;

import com.example.fridiet.dto.MealRecordRequest;
import com.example.fridiet.dto.MealRecordResponse;
import com.example.fridiet.dto.MealRecordUpdateRequest;
import com.example.fridiet.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /**
     * 식사 기록 저장 및 재고 자동 차감
     */
    @PostMapping("/meals")
    public ResponseEntity<MealRecordResponse> recordMeal(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MealRecordRequest request) {

        MealRecordResponse response = calendarService.recordMealAndDeductStock(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 사용자별 식사 기록 히스토리 조회
     */
    @GetMapping("/meals")
    public ResponseEntity<List<MealRecordResponse>> getMealHistory(@AuthenticationPrincipal Long userId) {
        List<MealRecordResponse> response = calendarService.getMealHistory(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 식사 기록 수정
     */
    @PutMapping("/{calendarId}")
    public ResponseEntity<Void> updateMeal(
            @PathVariable Long calendarId,
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid MealRecordUpdateRequest request) {

        calendarService.updateMeal(calendarId, userId, request);

        return ResponseEntity.ok().build();
    }

    /**
     * 식사 기록 삭제
     */
    @DeleteMapping("/{calendarId}")
    public ResponseEntity<Void> deleteMeal(
            @PathVariable Long calendarId,
            @AuthenticationPrincipal Long userId) {

        calendarService.deleteMeal(calendarId, userId);

        return ResponseEntity.noContent().build();
    }
}
