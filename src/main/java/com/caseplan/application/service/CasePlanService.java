package com.caseplan.application.service;

import com.caseplan.application.port.in.CreateCasePlanCommand;
import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.WarningException;
import com.caseplan.domain.model.*;
import com.caseplan.adapter.out.persistence.*;
import com.caseplan.application.port.out.QueuePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CasePlanService {

    private final CasePlanRepo casePlanRepo;
    private final CaseInfoRepo caseInfoRepo;
    private final ClientRepo clientRepo;
    private final AttorneyRepo attorneyRepo;
    private final QueuePort queuePort;

    public List<CasePlan> listAll() {
        return casePlanRepo.findAllByOrderByCreatedAtDesc();
    }

    public Page<CasePlan> listPage(int page, int pageSize, String status, String patientName) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.max(pageSize, 1);
        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize);
        return casePlanRepo.search(normalizeOptional(status), normalizeOptional(patientName), pageable);
    }

    public Page<CasePlan> listByClientIdPage(Long clientId, int page, int pageSize, String status) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.max(pageSize, 1);
        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize);
        return casePlanRepo.findByClientIdAndStatus(clientId, normalizeOptional(status), pageable);
    }

    @SuppressWarnings("null")
    public Optional<CasePlan> getById(Long id) {
        return casePlanRepo.findById(id);
    }

    @SuppressWarnings("null")
    public CreateCasePlanResult create(CreateCasePlanCommand command) {
        List<WarningException> warnings = new ArrayList<>();

        Optional<Attorney> existingAttorneyByBar = attorneyRepo.findByBarNumber(command.getBarNumber());
        Attorney attorney;
        if (existingAttorneyByBar.isPresent()) {
            Attorney existing = existingAttorneyByBar.get();
            if (!existing.getName().equals(command.getAttorneyName())) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("existingAttorney", existing);
                throw new BlockException(
                        "ATTORNEY_NAME_MISMATCH",
                        "Attorney with barNumber " + command.getBarNumber()
                                + " already exists with different name: " + existing.getName(),
                        detail
                );
            }
            attorney = existing;
        } else {
            attorney = new Attorney();
            attorney.setName(command.getAttorneyName());
            attorney.setBarNumber(command.getBarNumber());
            attorney = attorneyRepo.save(attorney);
        }

        Client client;
        if (command.getClientIdNumber() != null && !command.getClientIdNumber().isEmpty()) {
            Optional<Client> existingClientByIdNumber = clientRepo.findByIdNumber(command.getClientIdNumber());
            if (existingClientByIdNumber.isPresent()) {
                Client existing = existingClientByIdNumber.get();
                if (existing.getFirstName().equals(command.getClientFirstName())
                        && existing.getLastName().equals(command.getClientLastName())) {
                    client = existing;
                } else {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("existingClient", existing);
                    WarningException warning = new WarningException(
                            "CLIENT_ID_NAME_MISMATCH",
                            "Client with idNumber " + command.getClientIdNumber()
                                    + " already exists with different name: "
                                    + existing.getFirstName() + " " + existing.getLastName(),
                            detail
                    );
                    if (command.getConfirm() == null || !command.getConfirm()) {
                        throw warning;
                    }
                    warnings.add(warning);
                    client = existing;
                }
            } else {
                client = resolveClientByName(command, warnings);
            }
        } else {
            client = resolveClientByName(command, warnings);
        }

        LocalDate today = LocalDate.now();
        Instant startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        String opposingParty = command.getOpposingParty() != null ? command.getOpposingParty() : "";
        String primaryCause = command.getPrimaryCauseOfAction() != null ? command.getPrimaryCauseOfAction() : "";

        List<CaseInfo> duplicateCases = caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                client.getId(), primaryCause, opposingParty, startOfDay, endOfDay);
        if (!duplicateCases.isEmpty()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("existingCase", duplicateCases.get(0));
            throw new BlockException(
                    "DUPLICATE_CASE_SAME_DAY",
                    "Duplicate case: same client, cause of action, opposing party, and same day already exists",
                    detail
            );
        }

        List<CaseInfo> similarCases = caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                client.getId(), primaryCause, opposingParty, Instant.ofEpochMilli(0), startOfDay);
        if (!similarCases.isEmpty()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("existingCase", similarCases.get(0));
            WarningException warning = new WarningException(
                    "SIMILAR_CASE_DIFFERENT_DAY",
                    "Similar case exists on different day. This might be a case update or reopening.",
                    detail
            );
            if (command.getConfirm() == null || !command.getConfirm()) {
                throw warning;
            }
            warnings.add(warning);
        }

        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setClient(client);
        caseInfo.setAttorney(attorney);
        caseInfo.setCaseNumber(normalizeOptional(command.getCaseNumber()));
        caseInfo.setPrimaryCauseOfAction(command.getPrimaryCauseOfAction());
        caseInfo.setOpposingParty(command.getOpposingParty());
        caseInfo.setRemedySought(command.getRemedySought());
        caseInfo.setAdditionalCauses(command.getAdditionalCauses());
        caseInfo.setPriorLegalActions(command.getPriorLegalActions());
        caseInfo.setCaseDocuments(command.getCaseDocuments());
        caseInfo.setReferringSource(command.getReferringSource());
        caseInfoRepo.save(caseInfo);

        CasePlan casePlan = new CasePlan();
        casePlan.setCaseInfo(caseInfo);
        casePlan.setStatus("pending");
        casePlanRepo.save(casePlan);

        Long planId = casePlan.getId();
        if (planId != null) {
            queuePort.enqueue(String.valueOf(planId));
        }

        return new CreateCasePlanResult(casePlan, warnings);
    }

    @SuppressWarnings("null")
    public CasePlan createCasePlan(CreateCasePlanCommand command) {
        return create(command).getCasePlan();
    }

    private Client resolveClientByName(CreateCasePlanCommand command, List<WarningException> warnings) {
        Optional<Client> existingClientByName = clientRepo.findByFirstNameAndLastName(
                command.getClientFirstName(), command.getClientLastName());
        if (existingClientByName.isPresent()) {
            Client existing = existingClientByName.get();
            if (command.getClientIdNumber() != null && !command.getClientIdNumber().isEmpty()
                    && existing.getIdNumber() != null
                    && !existing.getIdNumber().equals(command.getClientIdNumber())) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("existingClient", existing);
                WarningException warning = new WarningException(
                        "CLIENT_NAME_ID_MISMATCH",
                        "Client with same name but different idNumber exists. Existing idNumber: " + existing.getIdNumber(),
                        detail
                );
                if (command.getConfirm() == null || !command.getConfirm()) {
                    throw warning;
                }
                warnings.add(warning);
            }
            return existing;
        }

        Client client = new Client();
        client.setFirstName(command.getClientFirstName());
        client.setLastName(command.getClientLastName());
        client.setIdNumber(command.getClientIdNumber());
        return clientRepo.save(client);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @SuppressWarnings("null")
    public Map<String, Object> getStatus(Long id) {
        Optional<CasePlan> optional = casePlanRepo.findById(id);
        if (!optional.isPresent()) {
            return null;
        }

        CasePlan casePlan = optional.get();
        Map<String, Object> body = new HashMap<>();
        body.put("status", casePlan.getStatus());

        if ("completed".equals(casePlan.getStatus())) {
            body.put("content", casePlan.getGeneratedPlan());
        } else if ("failed".equals(casePlan.getStatus())) {
            body.put("error", casePlan.getErrorMessage());
        }

        return body;
    }

    @SuppressWarnings("null")
    public Optional<CasePlan> retryFailed(Long id) {
        Optional<CasePlan> optional = casePlanRepo.findById(id);
        if (!optional.isPresent()) {
            return Optional.empty();
        }

        CasePlan casePlan = optional.get();
        if (!"failed".equals(casePlan.getStatus())) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("currentStatus", casePlan.getStatus());
            throw new BlockException(
                    "CASEPLAN_RETRY_NOT_ALLOWED",
                    "Only failed caseplans can be retried",
                    detail
            );
        }

        casePlan.setStatus("pending");
        casePlan.setErrorMessage(null);
        casePlan.setGeneratedPlan(null);
        CasePlan saved = casePlanRepo.save(casePlan);

        if (saved.getId() != null) {
            queuePort.enqueue(String.valueOf(saved.getId()));
        }
        return Optional.of(saved);
    }

    @SuppressWarnings("null")
    public Optional<CasePlan> getForDownload(Long id) {
        Optional<CasePlan> optional = casePlanRepo.findById(id);
        if (!optional.isPresent()) {
            return Optional.empty();
        }

        CasePlan casePlan = optional.get();
        String content = casePlan.getGeneratedPlan();
        if (!"completed".equals(casePlan.getStatus()) || content == null || content.trim().isEmpty()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("currentStatus", casePlan.getStatus());
            throw new BlockException(
                    "CASEPLAN_NOT_READY",
                    "Caseplan is not ready for download",
                    detail
            );
        }

        return Optional.of(casePlan);
    }
}
