package com.caseplan.core.repo;

import com.caseplan.core.entity.CasePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CasePlanRepo extends JpaRepository<CasePlan, Long> {
    List<CasePlan> findAllByOrderByCreatedAtDesc();
    Optional<CasePlan> findFirstByStatusOrderByCreatedAtAsc(String status);
    List<CasePlan> findByStatusAndUpdatedAtBefore(String status, Instant before);
}
