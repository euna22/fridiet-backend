package com.example.fridiet.repository;

import com.example.fridiet.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.id=:id")
    Optional<InventoryBatch> findForUpdate(@Param("id") Long id);

    // fridgeId 냉장고의 ACTIVE 재고 목록을 유통기한 순으로 정렬하여 조회
    List<InventoryBatch> findByFridgeIdAndStatusOrderByConsumeByDateAsc(Long fridgeId, String status);

    // statue = ACTIVE인 재고 목록 조회
    List<InventoryBatch> findAllByStatus(String status);

    @Query("select b from InventoryBatch b " +
            "left join fetch b.ingredientMaster " +
            "where b.fridge.id = :fridgeId " +
            "and b.status = :status " +
            "and b.consumeByDate between :startDate and :endDate " +
            "order by b.consumeByDate asc")
    List<InventoryBatch> findExpiringBatches(
            @Param("fridgeId") Long fridgeId,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("select b from InventoryBatch b " +
            "join fetch b.fridge f " +
            "left join fetch b.ingredientMaster m " +
            "where b.status = 'ACTIVE' " +
            "and b.consumeByDate BETWEEN :today AND :targetDate ")
    List<InventoryBatch> findBatchesExpiringOn(
            @Param("today") LocalDate today,
            @Param("targetDate") LocalDate targetDate);

    // 동일 냉장고 + 동일 식재료 + 동일 유통기한 + ACTIVE 상태인 배치 유무 조회
    Optional<InventoryBatch> findByFridgeIdAndIngredientMasterIdAndConsumeByDateAndStatus(
            Long fridgeId, Long ingredientMasterId, LocalDate consumeByDate, String status
    );

    // 동일 냉장고 + 동일 식재료명 + 동일 유통기한 + ACTIVE 상태인 배치 유무 조회
    Optional<InventoryBatch> findByFridgeIdAndIngredientNameAndConsumeByDateAndStatus(
            Long fridgeId, String ingredientName, LocalDate consumeByDate, String status
    );

    // 특정 냉장고의 ACTIVE 재고 목록 조회
    @Query("SELECT b FROM InventoryBatch b " +
            "WHERE b.fridge.id = :fridgeId AND b.status IN ('ACTIVE', 'EXPIRED')")
    List<InventoryBatch> findAllActiveByFridgeId(@Param("fridgeId") Long fridgeId);

    // 키워드 검색
    @Query("SELECT b FROM InventoryBatch b " +
            "WHERE b.fridge.id = :fridgeId " +
            "AND b.status = 'ACTIVE' " +
            "AND LOWER(b.ingredientName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<InventoryBatch> searchByFridgeIdAndKeyword(
            @Param("fridgeId") Long fridgeId,
            @Param("keyword") String keyword
    );

    @Query("select (count(b) > 0) from InventoryBatch b " +
            "left join b.ingredientMaster m " +
            "where b.fridge.id = :fridgeId " +
            "and b.status = 'ACTIVE' " +
            "and (lower(b.ingredientName) = lower(:ingredientName) " +
            "or lower(m.canonicalName) = lower(:ingredientName))")
    boolean existsActiveIngredientByFridgeIdAndName(
            @Param("fridgeId") Long fridgeId,
            @Param("ingredientName") String ingredientName
    );
}
