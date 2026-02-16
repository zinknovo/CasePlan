package com.caseplan.adapter.in.web.controller;

import com.caseplan.adapter.in.web.dto.CreateCasePlanRequest;
import com.caseplan.application.port.in.CreateCasePlanCommand;
import com.caseplan.domain.model.CasePlan;
import com.caseplan.adapter.in.web.response.PageResponseBuilder;
import com.caseplan.common.exception.WarningException;
import com.caseplan.common.exception.response.SuccessWithWarnings;
import com.caseplan.application.service.CreateCasePlanResult;
import com.caseplan.application.service.CasePlanService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/caseplans")
@RequiredArgsConstructor
public class CasePlanController {

    private final CasePlanService casePlanService;

    @GetMapping
    public Map<String, Object> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(name = "patient_name", required = false) String patientName) {
        var plansPage = casePlanService.listPage(page, pageSize, status, patientName);
        return PageResponseBuilder.from(plansPage, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CasePlan> getById(@PathVariable @NonNull Long id) {
        Optional<CasePlan> optional = casePlanService.getById(id);
        if (optional.isPresent()) {
            return ResponseEntity.ok(optional.get());
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable @NonNull Long id) {
        Map<String, Object> body = casePlanService.getStatus(id);
        if (body == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable @NonNull Long id) {
        Optional<CasePlan> optional = casePlanService.getForDownload(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CasePlan casePlan = optional.get();
        byte[] body = casePlan.getGeneratedPlan().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"caseplan-" + id + ".txt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retry(@PathVariable @NonNull Long id) {
        Optional<CasePlan> optional = casePlanService.retryFailed(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", optional.get().getId());
        body.put("status", optional.get().getStatus());
        body.put("message", "retry accepted");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping
    @SuppressWarnings("null")
    public ResponseEntity<?> create(@RequestBody @Valid CreateCasePlanRequest request) {
        CreateCasePlanResult result = casePlanService.create(toCommand(request));
        CasePlan casePlan = result.getCasePlan();
        List<WarningException> warnings = result.getWarnings();
        if (!warnings.isEmpty()) {
            SuccessWithWarnings<CasePlan> response = new SuccessWithWarnings<>(casePlan, warnings);
            return ResponseEntity.status(HttpStatus.CREATED).body(response.toMap());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(casePlan);
    }

    private CreateCasePlanCommand toCommand(CreateCasePlanRequest request) {
        CreateCasePlanCommand command = new CreateCasePlanCommand();
        command.setClientFirstName(request.getClientFirstName());
        command.setClientLastName(request.getClientLastName());
        command.setClientIdNumber(request.getClientIdNumber());
        command.setAttorneyName(request.getAttorneyName());
        command.setBarNumber(request.getBarNumber());
        command.setReferringSource(request.getReferringSource());
        command.setDocketNumber(request.getDocketNumber());
        command.setPrimaryCauseOfAction(request.getPrimaryCauseOfAction());
        command.setOpposingParty(request.getOpposingParty());
        command.setRemedySought(request.getRemedySought());
        command.setAdditionalCauses(request.getAdditionalCauses());
        command.setPriorLegalActions(request.getPriorLegalActions());
        command.setCaseDocuments(request.getCaseDocuments());
        command.setConfirm(request.getConfirm());
        return command;
    }
}
