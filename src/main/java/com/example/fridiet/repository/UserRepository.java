package com.example.fridiet.repository;

import com.example.fridiet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findForFeedbackUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    //socialId로 기존 회원이 있는지 찾는 메서드
    Optional<User> findBySocialId(String socialId);

    Optional<User> findBySocialIdAndIsDeletedFalse(String socialId);

    // 소셜 계정이 이미 연동되어 있는지 확인하는 메서드
    boolean existsBySocialProviderAndSocialId(String socialProvider, String socialId);

    // 일반 로그인 시 이메일로 유저 정보를 찾는 메서드
    Optional<User> findByEmail(String email);

    // 회원가입 시 이메일 중복 체크를 위한 메서드
    boolean existsByEmail(String email);
}
