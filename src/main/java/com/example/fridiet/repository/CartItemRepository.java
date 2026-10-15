package com.example.fridiet.repository;

import com.example.fridiet.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CartItem i where i.id=:id")
    java.util.Optional<CartItem> findForUpdate(@Param("id") Long id);

    // 장바구니 내 삭제되지 않은 항목들만 조회 (NEEDED, BOUGHT)
    List<CartItem> findAllByCartIdAndStateNot(Long cartId, String state);

    @Query("select (count(i) > 0) from CartItem i " +
            "where i.cart.fridge.id = :fridgeId " +
            "and i.cart.status = 'ACTIVE' " +
            "and lower(i.itemName) = lower(:itemName) " +
            "and i.state <> 'REMOVED'")
    boolean existsActiveItemByFridgeIdAndItemName(
            @Param("fridgeId") Long fridgeId,
            @Param("itemName") String itemName
    );
}
