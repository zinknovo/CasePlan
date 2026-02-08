package com.caseplan;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CasePlanRepo extends JpaRepository<CasePlan, Long> {
    List<CasePlan> findAllByOrderByCreatedAtDesc();
}
