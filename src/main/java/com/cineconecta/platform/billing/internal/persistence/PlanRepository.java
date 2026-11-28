package com.cineconecta.platform.billing.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PlanRepository extends JpaRepository<PlanEntity, Integer> {

    List<PlanEntity> findByIsActiveOrderByPriceAsc(String isActive);

    List<PlanEntity> findByPlanIdIn(Collection<Integer> planIds);

    List<PlanEntity> findAllByOrderByPriceAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndPlanIdNot(String name, Integer planId);
}
