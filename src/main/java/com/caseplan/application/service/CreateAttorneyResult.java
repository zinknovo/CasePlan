package com.caseplan.application.service;

import com.caseplan.domain.model.Attorney;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateAttorneyResult {
    private final Attorney attorney;
    private final boolean created;
}
