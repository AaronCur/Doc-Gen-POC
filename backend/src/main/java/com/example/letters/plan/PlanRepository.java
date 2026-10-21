package com.example.letters.plan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanRepository extends JpaRepository<Plan, String> {

    List<Plan> findAllByPlanType_IdOrderByNameAsc(String planTypeId);
}
