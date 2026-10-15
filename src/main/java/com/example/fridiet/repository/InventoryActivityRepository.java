package com.example.fridiet.repository;
import com.example.fridiet.entity.InventoryActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface InventoryActivityRepository extends JpaRepository<InventoryActivity, Long> {
    List<InventoryActivity> findByMealEventIdAndCancelledAtIsNull(Long mealEventId);
    List<InventoryActivity> findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            Long userId, LocalDateTime start, LocalDateTime end);
}
