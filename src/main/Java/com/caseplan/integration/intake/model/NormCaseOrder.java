package com.caseplan.integration.intake.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Normalized format for an intake case order. Business logic only recognises this format.
 * Raw data string is kept for troubleshooting.
 */
@Getter
@Setter
@NoArgsConstructor
public class NormCaseOrder {

    private NormClient client;
    private NormAttorney attorney;
    private NormCaseInfo caseInfo;
    private NormCarePlan carePlan;
    /** Original raw payload (JSON/XML string) for debugging and audit. */
    private String rawData;
}
