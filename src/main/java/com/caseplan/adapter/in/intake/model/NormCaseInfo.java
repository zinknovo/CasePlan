package com.caseplan.adapter.in.intake.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Normalized format for case info.
 */
@Getter
@Setter
@NoArgsConstructor
public class NormCaseInfo {

    private String caseNumber;
    private String primaryCauseOfAction;
    private String opposingParty;
    private String remedySought;
    private String additionalCauses;
    private String priorLegalActions;
    private String caseDocuments;
    private String referringSource;
}
