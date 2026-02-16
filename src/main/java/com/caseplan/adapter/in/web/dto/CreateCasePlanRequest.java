package com.caseplan.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
public class CreateCasePlanRequest {
    @NotBlank(message = "Client first name is required")
    private String clientFirstName;

    @NotBlank(message = "Client last name is required")
    private String clientLastName;

    private String clientIdNumber;

    @NotBlank(message = "Attorney name is required")
    private String attorneyName;

    @NotBlank(message = "Bar number is required")
    private String barNumber;

    private String referringSource;

    // Optional official/court docket number.
    @JsonAlias("caseNumber")
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
