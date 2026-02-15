package com.caseplan.adapter.in.intake.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Normalized format for care plan / intake plan data (e.g. notes from source).
 */
@Getter
@Setter
@NoArgsConstructor
public class NormCarePlan {

    private String notes;
    private String status;
}
