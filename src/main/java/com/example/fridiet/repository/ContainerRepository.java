package com.example.fridiet.repository;

import com.example.fridiet.entity.Container;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContainerRepository extends JpaRepository<Container, Long> {

    Optional<Container> findByQrCodeToken(String qrCodeToken);

    Optional<Container> findByCurrentBatch_Id(Long batchId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Container c where c.qrCodeToken = :token")
    Optional<Container> findForUpdate(@org.springframework.data.repository.query.Param("token") String token);
}
