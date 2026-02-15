package com.caseplan.domain.model;

import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "dev_caseplans")
@Getter
@Setter
@NoArgsConstructor
public class CasePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "case_info_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CaseInfo caseInfo;

    private String status; // pending, processing, completed, failed

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
