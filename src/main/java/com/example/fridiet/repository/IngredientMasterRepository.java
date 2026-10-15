package com.example.fridiet.repository;

import com.example.fridiet.entity.IngredientMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IngredientMasterRepository extends JpaRepository<IngredientMaster, Long> {

    Optional<IngredientMaster> findByCanonicalName(String CanonicalName);
}
