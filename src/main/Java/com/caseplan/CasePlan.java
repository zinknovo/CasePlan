package com.caseplan;

import javax.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "case_plans")
@Getter
@Setter
@NoArgsConstructor
public class CasePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Input fields ---
    private String clientFirstName;
    private String clientLastName;
    private String referringAttorney;
    private String barNumber;
    private String caseNumber;
    private String primaryCauseOfAction;
    private String legalRemedySought;
    private String additionalCauses;      // comma-separated, keep it simple
    private String priorLegalActions;     // comma-separated

    @Column(columnDefinition = "TEXT")
    private String caseDocuments;         // pasted text for now, no file upload in MVP

    // --- Output fields ---
    private String status;                // pending, processing, completed, failed

    @Column(columnDefinition = "TEXT")
    private String generatedPlan;

    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = "pending";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
