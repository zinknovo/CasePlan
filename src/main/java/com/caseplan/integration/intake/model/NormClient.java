package com.caseplan.integration.intake.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Normalized format for client data. Business logic only uses this format.
 */
@Getter
@Setter
@NoArgsConstructor
public class NormClient {

    private String firstName;
    private String lastName;
    private String idNumber;
}
