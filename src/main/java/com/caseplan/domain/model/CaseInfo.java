package com.caseplan.domain.model;

import javax.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "dev_case_infos")
@Getter
@Setter
@NoArgsConstructor
public class CaseInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne
    @JoinColumn(name = "attorney_id")
    private Attorney attorney;

    @Column(name = "case_number")
    private String caseNumber;
    @Column(name = "service_number")
    private String serviceNumber;
    private String primaryCauseOfAction;
    private String opposingParty;
    @Column(name = "legal_remedy_sought")
    private String remedySought;
    private String additionalCauses;
    private String priorLegalActions;

    @Column(columnDefinition = "TEXT")
    private String caseDocuments;

    private String referringSource;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
