package com.caseplan.core.repo;

import com.caseplan.core.entity.CaseInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CaseInfoRepo extends JpaRepository<CaseInfo, Long> {
    Optional<CaseInfo> findByCaseNumber(String caseNumber);
    List<CaseInfo> findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
            Long clientId, String primaryCauseOfAction, String opposingParty, Instant start, Instant end);
}
