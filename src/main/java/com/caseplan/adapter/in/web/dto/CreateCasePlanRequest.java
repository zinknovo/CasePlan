package com.caseplan.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Getter
@Setter
@NoArgsConstructor
public class CreateCasePlanRequest {
    @NotBlank(message = "Client first name is required")
    @Pattern(regexp = "^[A-Za-z][A-Za-z .'-]{0,49}$", message = "Client first name format is invalid")
    private String clientFirstName;

    @NotBlank(message = "Client last name is required")
    @Pattern(regexp = "^[A-Za-z][A-Za-z .'-]{0,49}$", message = "Client last name format is invalid")
    private String clientLastName;

    private String clientIdNumber;

    @NotBlank(message = "Attorney name is required")
    @Pattern(regexp = "^[A-Za-z][A-Za-z .'-]{0,49}$", message = "Attorney name format is invalid")
    private String attorneyName;

    @NotBlank(message = "Bar number is required")
    @Pattern(regexp = "^BAR-\\d{8}-\\d{4}$", message = "Bar number must match BAR-12345678-1234")
    private String barNumber;

    private String referringSource;

    // Optional official/court docket number.
    @JsonAlias("caseNumber")
    @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9-]{1,39}$", message = "Docket number format is invalid")
    private String docketNumber;

    @NotBlank(message = "Primary cause of action is required")
    private String primaryCauseOfAction;

    private String opposingParty;

    @NotBlank(message = "Legal remedy sought is required")
    private String remedySought;

    private String additionalCauses;
    private String priorLegalActions;
    private String caseDocuments;
    private Boolean confirm;
}
