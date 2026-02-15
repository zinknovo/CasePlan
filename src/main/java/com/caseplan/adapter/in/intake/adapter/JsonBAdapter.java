package com.caseplan.adapter.in.intake.adapter;

import com.caseplan.adapter.in.intake.model.NormAttorney;
import com.caseplan.adapter.in.intake.model.NormCarePlan;
import com.caseplan.adapter.in.intake.model.NormCaseInfo;
import com.caseplan.adapter.in.intake.model.NormCaseOrder;
import com.caseplan.adapter.in.intake.model.NormClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adapter for JSON format B: ClientFirstName, ClientLastName, BarNumber, etc.
 */
@Component
public class JsonBAdapter extends BaseIntakeAdapter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSourceName() {
        return "jsonB";
    }

    @Override
    protected Object parse(String rawData) {
        try {
            return objectMapper.readValue(rawData, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("JsonB: invalid JSON", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NormCaseOrder transform(Object parsed) {
        Map<String, Object> map = (Map<String, Object>) parsed;

        NormClient client = new NormClient();
        client.setFirstName(getString(map, "ClientFirstName"));
        client.setLastName(getString(map, "ClientLastName"));
        client.setIdNumber(getString(map, "ClientIdNumber"));

        NormAttorney attorney = new NormAttorney();
        attorney.setName(getString(map, "AttorneyName"));
        attorney.setBarNumber(getString(map, "BarNumber"));

        NormCaseInfo caseInfo = new NormCaseInfo();
        caseInfo.setCaseNumber(getString(map, "CaseNumber"));
        caseInfo.setPrimaryCauseOfAction(getString(map, "PrimaryCauseOfAction"));
        caseInfo.setOpposingParty(getString(map, "OpposingParty"));
        caseInfo.setRemedySought(getString(map, "RemedySought"));
        caseInfo.setAdditionalCauses(getString(map, "AdditionalCauses"));
        caseInfo.setPriorLegalActions(getString(map, "PriorLegalActions"));
        caseInfo.setCaseDocuments(getString(map, "CaseDocuments"));
        caseInfo.setReferringSource(getString(map, "ReferringSource"));

        NormCarePlan carePlan = new NormCarePlan();
        carePlan.setNotes(getString(map, "CarePlanNotes"));
        carePlan.setStatus("pending");

        NormCaseOrder order = new NormCaseOrder();
        order.setClient(client);
        order.setAttorney(attorney);
        order.setCaseInfo(caseInfo);
        order.setCarePlan(carePlan);
        return order;
    }

    @Override
    protected void validate(NormCaseOrder order) {
        if (order.getClient() == null || order.getAttorney() == null || order.getCaseInfo() == null) {
            throw new IllegalArgumentException("JsonB: client, attorney and caseInfo are required");
        }
        if (blank(order.getClient().getFirstName()) || blank(order.getClient().getLastName())) {
            throw new IllegalArgumentException("JsonB: ClientFirstName and ClientLastName are required");
        }
        if (blank(order.getAttorney().getBarNumber())) {
            throw new IllegalArgumentException("JsonB: BarNumber is required");
        }
        if (blank(order.getCaseInfo().getCaseNumber())) {
            throw new IllegalArgumentException("JsonB: CaseNumber is required");
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString().trim();
    }

    private static boolean blank(String s) {
        return s == null || s.isEmpty();
    }
}
