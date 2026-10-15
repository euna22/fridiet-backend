package com.example.fridiet.repository;

import com.example.fridiet.entity.MealConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealConsumptionRepository extends JpaRepository<MealConsumption, Long> {

    // 특정 식사 이벤트의 소모 상세 내역 조회
    List<MealConsumption> findAllByCalendarId(Long calendarId);

    
    // 특정 식사 이벤트의 소모 내역 삭제
    void deleteAllByCalendarId(Long calendarId);
}