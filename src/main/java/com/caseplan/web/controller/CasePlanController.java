package com.caseplan.web.controller;

import com.caseplan.web.dto.CreateCasePlanRequest;
import com.caseplan.core.entity.*;
import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.WarningException;
import com.caseplan.common.exception.response.SuccessWithWarnings;
import com.caseplan.core.repo.*;
import com.caseplan.core.service.CasePlanService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/caseplans")
@RequiredArgsConstructor
public class CasePlanController {

    private final CasePlanService casePlanService;
    private final CasePlanRepo casePlanRepo;
    private final CaseInfoRepo caseInfoRepo;
    private final ClientRepo clientRepo;
    private final AttorneyRepo attorneyRepo;
    private final StringRedisTemplate redisTemplate;

    private static final String QUEUE_KEY = "caseplan:pending";

    @GetMapping
    public List<CasePlan> listAll() {
        return casePlanService.listAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CasePlan> getById(@PathVariable @NonNull Long id) {
        java.util.Optional<CasePlan> optional = casePlanService.getById(id);
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

    @PostMapping
    @SuppressWarnings("null")
    public ResponseEntity<?> create(@RequestBody @Valid CreateCasePlanRequest request) {
        List<WarningException> warnings = new ArrayList<>();

        // ========== Attorney lookup/create ==========
        Optional<Attorney> existingAttorneyByBar = attorneyRepo.findByBarNumber(request.getBarNumber());
        Attorney attorney;
        if (existingAttorneyByBar.isPresent()) {
            Attorney existing = existingAttorneyByBar.get();
            if (!existing.getName().equals(request.getAttorneyName())) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("existingAttorney", existing);
                throw new BlockException("ATTORNEY_NAME_MISMATCH",
                        "Attorney with barNumber " + request.getBarNumber() + " already exists with different name: " + existing.getName(),
                        detail);
            }
            attorney = existing;
        } else {
            attorney = new Attorney();
            attorney.setName(request.getAttorneyName());
            attorney.setBarNumber(request.getBarNumber());
            attorney = attorneyRepo.save(attorney);
        }

        // ========== Client lookup/create ==========
        Client client = null;
        if (request.getClientIdNumber() != null && !request.getClientIdNumber().isEmpty()) {
            Optional<Client> existingClientByIdNumber = clientRepo.findByIdNumber(request.getClientIdNumber());
            if (existingClientByIdNumber.isPresent()) {
                Client existing = existingClientByIdNumber.get();
                if (existing.getFirstName().equals(request.getClientFirstName()) &&
                    existing.getLastName().equals(request.getClientLastName())) {
                    client = existing;
                } else {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("existingClient", existing);
                    WarningException warning = new WarningException("CLIENT_ID_NAME_MISMATCH",
                            "Client with idNumber " + request.getClientIdNumber() + " already exists with different name: " + existing.getFirstName() + " " + existing.getLastName(),
                            detail);
                    if (request.getConfirm() == null || !request.getConfirm()) {
                        throw warning;
                    }
                    warnings.add(warning);
                    client = existing;
                }
            }
        }

        if (client == null) {
            Optional<Client> existingClientByName = clientRepo.findByFirstNameAndLastName(
                    request.getClientFirstName(), request.getClientLastName());
            if (existingClientByName.isPresent()) {
                Client existing = existingClientByName.get();
                if (request.getClientIdNumber() != null && !request.getClientIdNumber().isEmpty() &&
                    existing.getIdNumber() != null && !existing.getIdNumber().equals(request.getClientIdNumber())) {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("existingClient", existing);
                    WarningException warning = new WarningException("CLIENT_NAME_ID_MISMATCH",
                            "Client with same name but different idNumber exists. Existing idNumber: " + existing.getIdNumber(),
                            detail);
                    if (request.getConfirm() == null || !request.getConfirm()) {
                        throw warning;
                    }
                    warnings.add(warning);
                    client = existing;
                } else {
                    client = existing;
                }
            } else {
                client = new Client();
                client.setFirstName(request.getClientFirstName());
                client.setLastName(request.getClientLastName());
                client.setIdNumber(request.getClientIdNumber());
                client = clientRepo.save(client);
            }
        }

        // ========== CaseInfo duplicate/similar check ==========
        LocalDate today = LocalDate.now();
        Instant startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        String opposingParty = request.getOpposingParty() != null ? request.getOpposingParty() : "";
        String primaryCause = request.getPrimaryCauseOfAction() != null ? request.getPrimaryCauseOfAction() : "";

        List<CaseInfo> duplicateCases = caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                client.getId(), primaryCause, opposingParty, startOfDay, endOfDay);

        if (!duplicateCases.isEmpty()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("existingCase", duplicateCases.get(0));
            throw new BlockException("DUPLICATE_CASE_SAME_DAY",
                    "Duplicate case: same client, cause of action, opposing party, and same day already exists",
                    detail);
        }

        List<CaseInfo> similarCases = caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                client.getId(), primaryCause, opposingParty, Instant.ofEpochMilli(0), startOfDay);

        if (!similarCases.isEmpty()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("existingCase", similarCases.get(0));
            WarningException warning = new WarningException("SIMILAR_CASE_DIFFERENT_DAY",
                    "Similar case exists on different day. This might be a case update or reopening.",
                    detail);
            if (request.getConfirm() == null || !request.getConfirm()) {
                throw warning;
            }
            warnings.add(warning);
        }

        // ========== Create CaseInfo ==========
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setClient(client);
        caseInfo.setAttorney(attorney);
        caseInfo.setCaseNumber(normalizeOptional(request.getCaseNumber()));
        caseInfo.setPrimaryCauseOfAction(request.getPrimaryCauseOfAction());
        caseInfo.setOpposingParty(request.getOpposingParty());
        caseInfo.setLegalRemedySought(request.getLegalRemedySought());
        caseInfo.setAdditionalCauses(request.getAdditionalCauses());
        caseInfo.setPriorLegalActions(request.getPriorLegalActions());
        caseInfo.setCaseDocuments(request.getCaseDocuments());
        caseInfo.setReferringSource(request.getReferringSource());
        caseInfoRepo.save(caseInfo);

        // ========== Create CasePlan ==========
        CasePlan casePlan = new CasePlan();
        casePlan.setCaseInfo(caseInfo);
        casePlan.setStatus("pending");
        casePlanRepo.save(casePlan);

        // ========== Push to queue ==========
        Long planId = casePlan.getId();
        if (planId != null) {
            redisTemplate.opsForList().rightPush(QUEUE_KEY, String.valueOf(planId));
        }

        // ========== Return result (wrap with warnings if any) ==========
        if (!warnings.isEmpty()) {
            SuccessWithWarnings<CasePlan> response = new SuccessWithWarnings<>(casePlan, warnings);
            return ResponseEntity.status(HttpStatus.CREATED).body(response.toMap());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(casePlan);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
