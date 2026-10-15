package com.example.fridiet.repository;

import com.example.fridiet.entity.Calendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalendarRepository extends JpaRepository<Calendar, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Calendar c where c.id = :id")
    java.util.Optional<Calendar> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    java.util.Optional<Calendar> findByOwnerIdAndRequestKey(Long ownerId, String requestKey);

    // 특정 사용자의 식사 기록 조회
    List<Calendar> findAllByOwnerIdAndDeletedAtIsNullOrderByEatenAtDesc(Long userId);
}
