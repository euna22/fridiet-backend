package com.example.fridiet.service;

import com.example.fridiet.dto.FridgeCreateRequest;
import com.example.fridiet.dto.FridgeJoinRequest;
import com.example.fridiet.dto.FridgeSummaryResponse;
import com.example.fridiet.entity.Fridge;
import com.example.fridiet.entity.FridgeMember;
import com.example.fridiet.entity.User;
import com.example.fridiet.repository.FridgeMemberRepository;
import com.example.fridiet.repository.FridgeRepository;
import com.example.fridiet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FridgeService {

    private final FridgeRepository fridgeRepository;
    private final UserRepository userRepository;
    private final FridgeMemberRepository fridgeMemberRepository;

    /**
     * 회원가입 시 기본 냉장고 생성
     */
    @Transactional
    public Fridge createDefaultFridge(User user) {

        // 냉장고 생성
        String defaultFridgeName = "냉장고";
        Fridge fridge = Fridge.builder()
                .name(defaultFridgeName)
                .owner(user)
                .build();
        Fridge savedFridge = fridgeRepository.save(fridge);

        // 냉장고 매핑
        FridgeMember fridgeMember = FridgeMember.builder()
                .fridge(savedFridge)
                .user(user)
                .role("OWNER")
                .status("ACCEPTED")
                .build();
        fridgeMemberRepository.save(fridgeMember);

        return savedFridge;
    }

    /**
     * 새 냉장고 추가
     */
    @Transactional
    public Fridge createFridge(Long userId, FridgeCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 사용자입니다."));

        // 냉장고 생성 및 저장
        Fridge fridge = Fridge.builder()
                .name(request.getName())
                .owner(user)
                .build();
        Fridge savedFridge = fridgeRepository.save(fridge);

        // 생성자를 냉장고 OWNER로 등록
        FridgeMember ownerMember = FridgeMember.builder()
                .fridge(savedFridge)
                .user(user)
                .role("OWNER")
                .status("ACCEPTED")
                .build();
        fridgeMemberRepository.save(ownerMember);

        // 사용자의 최초 냉장고일 경우 자동으로 대표 냉장고로 등록
        if(user.getMainFridgeId() == null) {
            user.updateMainFridge(savedFridge.getId());
        }
        return savedFridge;
    }

    @Transactional
    public String generateInviteCode(Long ownerId, Long fridgeId) {
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 냉장고입니다."));

        if(!fridge.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("냉장고 주인만 초대 코드를 생성할 수 있습니다.");
        }

        String newCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        fridge.updateInviteCode(newCode);

        return newCode;
    }

    @Transactional
    public FridgeMember joinWithCode(Long userId, FridgeJoinRequest request) {
        Fridge fridge = fridgeRepository.findByInviteCode(request.getInviteCode())
                .orElseThrow(() -> new RuntimeException("유효하지 않은 초대 코드입니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 사용자입니다."));

        if(fridge.getOwner().getId().equals(userId)) {
            throw new RuntimeException("이미 이 냉장고의 주인입니다.");
        }

        if(fridgeMemberRepository.existsByFridgeIdAndUserId(fridge.getId(), userId)) {
            throw new RuntimeException("이미 이 냉장고의 멤버입니다.");
        }

        FridgeMember fridgeMember = FridgeMember.builder()
                .fridge(fridge)
                .user(user)
                .role("MEMBER")
                .status("ACCEPTED")
                .build();

        return fridgeMemberRepository.save(fridgeMember);
    }

    /**
     * 로그인한 사용자의 모든 냉장고 목록 조회(상단 드롭다운용)
     */
    @Transactional
    public List<FridgeSummaryResponse> getMyFridges(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다.ID: " + userId));

        List<FridgeMember> members = fridgeMemberRepository.findAllByUserIdWithFridge(userId);

        if(members.isEmpty()) {
            return List.of(); // 속한 냉장고가 없는 경우
        }

        // 대표 냉장고가 지정되어 있지 않다면 첫 번째 냉장고로 자동 설정
        Long currentMainFridgeId = user.getMainFridgeId();
        if(currentMainFridgeId == null) {
            currentMainFridgeId = members.getFirst().getFridge().getId();
            user.updateMainFridge(currentMainFridgeId);
        }

        final Long mainId = currentMainFridgeId;

        return members.stream()
                .map(fm -> FridgeSummaryResponse.of(
                        fm.getFridge().getId(),
                        fm.getFridge().getName(),
                        fm.getFridge().getId().equals(mainId) // 즐겨찾기 설정(대표 냉장고) 여부
                ))
                .toList();
    }

    /**
     * 대표 냉장고 변경
     */
    @Transactional
    public void updateMainFridge(Long userId, Long targetFridgeId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));

        // 해당 사용자가 실제로 그 냉장고의 멤버인지 권한 검증
        List<FridgeMember> members = fridgeMemberRepository.findAllByUserIdWithFridge(userId);
        boolean isMember = members.stream()
                .anyMatch(fm -> fm.getFridge().getId().equals(targetFridgeId));

        if(!isMember) {
            throw new IllegalArgumentException("해당 냉장고에 접근 권한이 없습니다. FridgeID: " + targetFridgeId);
        }

        // DB에 메인 냉장고로 저장
        user.updateMainFridge(targetFridgeId);
    }
}
