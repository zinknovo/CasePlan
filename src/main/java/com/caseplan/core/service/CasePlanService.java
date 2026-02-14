package com.caseplan.core.service;

import com.caseplan.web.dto.CreateCasePlanRequest;
import com.caseplan.core.entity.*;
import com.caseplan.core.repo.*;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
    private final StringRedisTemplate redisTemplate;

    private static final String QUEUE_KEY = "caseplan:pending";

    public List<CasePlan> listAll() {
        return casePlanRepo.findAllByOrderByCreatedAtDesc();
    }

    @SuppressWarnings("null")
    public Optional<CasePlan> getById(Long id) {
        return casePlanRepo.findById(id);
    }

    @SuppressWarnings("null")
    public CasePlan createCasePlan(CreateCasePlanRequest request) {
        // 1. Find or create Client
        Optional<Client> existingClient = clientRepo.findByFirstNameAndLastName(
                request.getClientFirstName(), request.getClientLastName());
        Client client;
        if (existingClient.isPresent()) {
            client = existingClient.get();
        } else {
            client = new Client();
            client.setFirstName(request.getClientFirstName());
            client.setLastName(request.getClientLastName());
            client = clientRepo.save(client);
        }

        // 2. Find or create Attorney by barNumber
        Optional<Attorney> existingAttorney = attorneyRepo.findByBarNumber(request.getBarNumber());
        Attorney attorney;
        if (existingAttorney.isPresent()) {
            attorney = existingAttorney.get();
        } else {
            attorney = new Attorney();
            attorney.setName(request.getAttorneyName());
            attorney.setBarNumber(request.getBarNumber());
            attorney = attorneyRepo.save(attorney);
        }

        // 3. Create CaseInfo
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setClient(client);
        caseInfo.setAttorney(attorney);
        caseInfo.setCaseNumber(request.getCaseNumber());
        caseInfo.setPrimaryCauseOfAction(request.getPrimaryCauseOfAction());
        caseInfo.setLegalRemedySought(request.getLegalRemedySought());
        caseInfo.setAdditionalCauses(request.getAdditionalCauses());
        caseInfo.setPriorLegalActions(request.getPriorLegalActions());
        caseInfo.setCaseDocuments(request.getCaseDocuments());
        caseInfo.setReferringSource(request.getReferringSource());
        caseInfoRepo.save(caseInfo);

        // 4. Create CasePlan as pending
        CasePlan casePlan = new CasePlan();
        casePlan.setCaseInfo(caseInfo);
        casePlan.setStatus("pending");
        casePlanRepo.save(casePlan);

        // 5. Push casePlan id to Redis queue for async processing
        Long planId = casePlan.getId();
        redisTemplate.opsForList().rightPush(QUEUE_KEY, planId.toString());

        return casePlan;
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
}
