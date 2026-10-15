package com.example.fridiet.repository;

import com.example.fridiet.entity.IngredientAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientAliasRepository extends JpaRepository<IngredientAlias, Long>{

    @Query("select a from IngredientAlias a join fetch a.ingredientMaster where a.aliasText = :aliasName")
    Optional<IngredientAlias> findByAliasName(@Param("aliasName") String aliasName);

    List<IngredientAlias> findAllByIngredientMaster_Id(Long ingredientMasterId);
}
