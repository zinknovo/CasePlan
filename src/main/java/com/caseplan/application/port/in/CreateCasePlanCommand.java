package com.caseplan.application.port.in;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCasePlanCommand {
    private String clientFirstName;
    private String clientLastName;
    private String clientIdNumber;
    private String attorneyName;
    private String barNumber;
    private String referringSource;
    private String docketNumber;
    private String primaryCauseOfAction;
    private String opposingParty;
    private String remedySought;
    private String additionalCauses;
    private String priorLegalActions;
    private String caseDocuments;
    private Boolean confirm;
}
