package com.example.fridiet.controller;

import com.example.fridiet.dto.FridgeCreateRequest;
import com.example.fridiet.dto.FridgeJoinRequest;
import com.example.fridiet.dto.FridgeSummaryResponse;
import com.example.fridiet.entity.Fridge;
import com.example.fridiet.entity.FridgeMember;
import com.example.fridiet.service.FridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/fridges")
@RequiredArgsConstructor
public class FridgeController {

    private final FridgeService fridgeService;

    /**
     * 냉장고 생성
     */
    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal Long userId, // 어노테이션이 시큐리티 장바구니에서 유저 ID 자동 추출
            @RequestBody FridgeCreateRequest request
    ) {
        try {
            Fridge fridge = fridgeService.createFridge(userId, request);
            return ResponseEntity.ok(fridge);
        } catch(Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 초대 코드 발급
     */
    @PostMapping("/{fridgeId}/code")
    public ResponseEntity<?> createCode(
            @AuthenticationPrincipal Long ownerId,
            @PathVariable("fridgeId") Long fridgeId) {
        try{
            String code = fridgeService.generateInviteCode(ownerId, fridgeId);
            return ResponseEntity.ok(Collections.singletonMap("inviteCode", code));
        } catch(Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 초대 코드로 조인
     */
    @PostMapping("/join")
    private ResponseEntity<?> joinFrdge(
            @AuthenticationPrincipal Long userId,
            @RequestBody FridgeJoinRequest request) {
        try {
            FridgeMember joinInfo = fridgeService.joinWithCode(userId, request);
            return ResponseEntity.ok(joinInfo);
        } catch(Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 내 냉장고 목록 조회 (상단 드롭다운 리스트용)
     */
    @GetMapping("/my")
    public ResponseEntity<List<FridgeSummaryResponse>> getMyFridges(@AuthenticationPrincipal Long userId) {
        List<FridgeSummaryResponse> response = fridgeService.getMyFridges(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/fridges/main?targetFridgeId={fridgeId}
     * 대표 냉장고 변경
     */
    @PatchMapping("/main")
    public ResponseEntity<Void> updateMainFridge(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long targetFridgeId) {

        fridgeService.updateMainFridge(userId, targetFridgeId);
        return ResponseEntity.ok().build();
    }
}
