package com.caseplan.application.service;

import com.caseplan.common.exception.WarningException;
import com.caseplan.domain.model.CasePlan;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class CreateCasePlanResult {
    private final CasePlan casePlan;
    private final List<WarningException> warnings;
}
