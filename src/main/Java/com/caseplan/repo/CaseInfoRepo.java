package com.caseplan.repo;

import com.caseplan.entity.CaseInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseInfoRepo extends JpaRepository<CaseInfo, Long> {
}
