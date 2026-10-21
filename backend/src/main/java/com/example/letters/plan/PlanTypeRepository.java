package com.example.letters.plan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanTypeRepository extends JpaRepository<PlanType, String> {

    List<PlanType> findAllByOrderByNameAsc();
}
