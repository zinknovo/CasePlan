package com.caseplan.adapter.out.persistence;

import com.caseplan.domain.model.CasePlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CasePlanRepo extends JpaRepository<CasePlan, Long> {
    List<CasePlan> findAllByOrderByCreatedAtDesc();
    List<CasePlan> findByStatus(String status);
    Optional<CasePlan> findFirstByStatusOrderByCreatedAtAsc(String status);
    List<CasePlan> findByStatusAndUpdatedAtBefore(String status, Instant before);

    @Query("SELECT cp FROM CasePlan cp " +
            "LEFT JOIN cp.caseInfo ci " +
            "LEFT JOIN ci.client c " +
            "WHERE (:status IS NULL OR cp.status = :status) " +
            "AND (:patientName IS NULL OR " +
            "LOWER(COALESCE(c.firstName, '')) LIKE LOWER(CONCAT('%', :patientName, '%')) OR " +
            "LOWER(COALESCE(c.lastName, '')) LIKE LOWER(CONCAT('%', :patientName, '%')) OR " +
            "LOWER(CONCAT(COALESCE(c.firstName, ''), ' ', COALESCE(c.lastName, ''))) LIKE LOWER(CONCAT('%', :patientName, '%'))) " +
            "ORDER BY cp.createdAt DESC")
    Page<CasePlan> search(
            @Param("status") String status,
            @Param("patientName") String patientName,
            Pageable pageable
    );

    @Query("SELECT cp FROM CasePlan cp " +
            "LEFT JOIN cp.caseInfo ci " +
            "WHERE ci.client.id = :clientId " +
            "AND (:status IS NULL OR cp.status = :status) " +
            "ORDER BY cp.createdAt DESC")
    Page<CasePlan> findByClientIdAndStatus(
            @Param("clientId") Long clientId,
            @Param("status") String status,
            Pageable pageable
    );
}
