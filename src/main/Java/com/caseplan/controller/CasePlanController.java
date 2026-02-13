package com.caseplan.controller;

import com.caseplan.entity.*;
import com.caseplan.repo.*;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/caseplans")
@RequiredArgsConstructor
public class CasePlanController {

    private final CasePlanRepo casePlanRepo;
    private final CaseInfoRepo caseInfoRepo;
    private final ClientRepo clientRepo;
    private final AttorneyRepo attorneyRepo;
    private final StringRedisTemplate redisTemplate;

    private static final String QUEUE_KEY = "caseplan:pending";

    // GET /api/caseplans — list all, with nested caseInfo -> client + attorney
    @GetMapping
    public List<CasePlan> listAll() {
        return casePlanRepo.findAllByOrderByCreatedAtDesc();
    }

    // GET /api/caseplans/{id} — get one by id
    @GetMapping("/{id}")
    public ResponseEntity<CasePlan> getById(@PathVariable @NonNull Long id) {
        Optional<CasePlan> optional = casePlanRepo.findById(id);

        if (optional.isPresent()) {
            CasePlan plan = optional.get();
            return ResponseEntity.ok(plan);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // POST /api/caseplans — create client, attorney, case, plan + push to queue
    @SuppressWarnings("null")
    @PostMapping
    public ResponseEntity<CasePlan> create(@RequestBody Map<String, String> input) {
        // 1. Find or create Client
        String firstName = input.get("clientFirstName");
        String lastName = input.get("clientLastName");
        Optional<Client> existingClient = clientRepo.findByFirstNameAndLastName(firstName, lastName);
        Client client;
        if (existingClient.isPresent()) {
            client = existingClient.get();
        } else {
            client = new Client();
            client.setFirstName(firstName);
            client.setLastName(lastName);
            client = clientRepo.save(client);
        }

        // 2. Find or create Attorney by barNumber
        String barNumber = input.get("barNumber");
        String attorneyName = input.get("referringAttorney");
        Optional<Attorney> existingAttorney = attorneyRepo.findByBarNumber(barNumber);
        Attorney attorney;
        if (existingAttorney.isPresent()) {
            attorney = existingAttorney.get();
        } else {
            attorney = new Attorney();
            attorney.setName(attorneyName);
            attorney.setBarNumber(barNumber);
            attorney = attorneyRepo.save(attorney);
        }

        // 3. Create CaseInfo
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setClient(client);
        caseInfo.setAttorney(attorney);
        caseInfo.setCaseNumber(input.get("caseNumber"));
        caseInfo.setPrimaryCauseOfAction(input.get("primaryCauseOfAction"));
        caseInfo.setLegalRemedySought(input.get("legalRemedySought"));
        caseInfo.setAdditionalCauses(input.get("additionalCauses"));
        caseInfo.setPriorLegalActions(input.get("priorLegalActions"));
        caseInfo.setCaseDocuments(input.get("caseDocuments"));
        caseInfo.setReferringSource(input.get("referringSource"));
        caseInfoRepo.save(caseInfo);

        // 4. Create CasePlan as pending
        CasePlan casePlan = new CasePlan();
        casePlan.setCaseInfo(caseInfo);
        casePlan.setStatus("pending");
        casePlanRepo.save(casePlan);

        // 5. Push casePlan id to Redis queue for async processing
        redisTemplate.opsForList().rightPush(QUEUE_KEY, casePlan.getId().toString());

        // 6. Return immediately — LLM will be called later by a queue consumer
        return ResponseEntity.status(HttpStatus.CREATED).body(casePlan);
    }

    // callDeepSeek removed — will be handled by a queue consumer later
}
