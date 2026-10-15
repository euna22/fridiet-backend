package com.example.fridiet.repository;

import com.example.fridiet.entity.Fridge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FridgeRepository extends JpaRepository<Fridge, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from Fridge f where f.id=:id")
    Optional<Fridge> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    //내가 owner인 냉장고 목록 조회 메소드
    List<Fridge> findByOwnerId(Long ownerId);
    Optional<Fridge> findByInviteCode(String inviteCode);
}
