package com.example.fridiet.repository;

import com.example.fridiet.entity.FcmNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FcmNotificationRepository extends JpaRepository<FcmNotification, Long> {

    List<FcmNotification> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
