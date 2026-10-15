package com.example.fridiet.repository;

import com.example.fridiet.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // 특정 냉장고의 ACTIVE 장바구니 조회
    List<Cart> findAllByFridgeIdAndStatus(Long fridgeId, String status);

    // 냉장고 내 특정 활성 장바구니 단건 조회
    Optional<Cart> findByIdAndFridgeIdAndStatus(Long id, Long fridgeId, String status);
}