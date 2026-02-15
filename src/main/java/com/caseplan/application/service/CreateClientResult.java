package com.caseplan.application.service;

import com.caseplan.domain.model.Client;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateClientResult {
    private final Client client;
    private final boolean created;
}
