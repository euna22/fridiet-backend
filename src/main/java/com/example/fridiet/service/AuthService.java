package com.example.fridiet.service;

import com.example.fridiet.dto.LoginRequest;
import com.example.fridiet.dto.RegisterRequest;
import com.example.fridiet.entity.Fridge;
import com.example.fridiet.entity.User;
import com.example.fridiet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FridgeService fridgeService;
    private final EmailAuthService emailAuthService;

    //일반 회원가입
    @Transactional
    public User register(RegisterRequest request) {

        // Reids에 이메일 인증 완료 상태가 남아있는지 확인
        emailAuthService.checkEmailIsVerified(request.getEmail());

        // 이메일 중복 체크
        if(userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        // 비밀번호 암호화 및 유저 생성
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .socialProvider("EMAIL")
                .healthCondition(request.getHealthCondition())
                .healthGoal(request.getHealthGoal())
                .isDeleted(false)
                .build();

        User savedUser = userRepository.save(user);

        // 기본 냉장고 자동 생성
        Fridge defaultFridge = fridgeService.createDefaultFridge(savedUser);

        // 대표 냉장고로 등록
        savedUser.updateMainFridge(defaultFridge.getId());

        // 가입이 완료되었으므로 Redis 인증 완료 상태 삭제
        emailAuthService.removeVerifiedStatus(request.getEmail());

        return savedUser;
    }

    // 일반 로그인
    @Transactional(readOnly = true)
    public User login(LoginRequest request) {
        // 이메일로 유저 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("존재하지 않는 이메일입니다."));

        // 탈퇴된 회원 체크
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new IllegalStateException("탈퇴한 계정입니다. 다시 회원가입을 진행해 주세요.");
        }

        //비밀번호 확인
        if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        return user; // 로그인 성공 시 유저 정보 반환
    }
}
