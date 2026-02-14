package com.caseplan.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

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

    @NotBlank(message = "Case number is required")
    @Size(min = 6, max = 6, message = "Case number must be 6 digits")
    @Pattern(regexp = "\\d{6}", message = "Case number must be 6 digits")
    private String caseNumber;

    @NotBlank(message = "Primary cause of action is required")
    private String primaryCauseOfAction;

    private String opposingParty;

    @NotBlank(message = "Legal remedy sought is required")
    private String legalRemedySought;

    private String additionalCauses;
    private String priorLegalActions;
    private String caseDocuments;
    private Boolean confirm;
}
