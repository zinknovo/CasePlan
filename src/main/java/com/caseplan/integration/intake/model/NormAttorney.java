package com.caseplan.integration.intake.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Normalized format for attorney data.
 */
@Getter
@Setter
@NoArgsConstructor
public class NormAttorney {

    private String name;
    private String barNumber;
}
