package com.example.fridiet.repository;

import com.example.fridiet.entity.FridgeMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface FridgeMemberRepository extends JpaRepository<FridgeMember, Long> {

    boolean existsByFridgeIdAndUserId(Long fridgeId, Long userId);
    boolean existsByFridgeIdAndUserIdAndStatus(Long fridgeId, Long userId, String status);

    @Query("SELECT fm FROM FridgeMember fm " +
            "JOIN FETCH fm.user u " +
            "WHERE fm.fridge.id = :fridgeId")
    List<FridgeMember> findByFridgeIdWithUser(@Param("fridgeId") Long fridgeId);

    //해당 사용자가 참여 중인 모든 냉장고 목록 조회
    @Query("SELECT fm FROM FridgeMember fm " +
            "JOIN FETCH fm.fridge f " +
            "WHERE fm.user.id = :userId")
    List<FridgeMember> findAllByUserIdWithFridge(@Param("userId") Long userId);

    Optional<FridgeMember> findByUser_Email(String email);
}
