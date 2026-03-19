package com.abco.taxassessment.domain.transaction.repository;

import com.abco.taxassessment.domain.transaction.domain.entity.TaxCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxCategoryRepository extends JpaRepository<TaxCategoryEntity, String> {

    Optional<TaxCategoryEntity> findByCode(String code);

    List<TaxCategoryEntity> findAllByActiveTrue();

    List<TaxCategoryEntity> findAllByParentCode(String parentCode);

    List<TaxCategoryEntity> findAllByParentCodeIsNull();
}
