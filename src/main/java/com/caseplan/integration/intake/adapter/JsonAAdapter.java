package com.caseplan.integration.intake.adapter;

import com.caseplan.integration.intake.model.NormAttorney;
import com.caseplan.integration.intake.model.NormCarePlan;
import com.caseplan.integration.intake.model.NormCaseInfo;
import com.caseplan.integration.intake.model.NormCaseOrder;
import com.caseplan.integration.intake.model.NormClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adapter for JSON format A: fname, lname, bar_num, etc.
 */
@Component
public class JsonAAdapter extends BaseIntakeAdapter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSourceName() {
        return "jsonA";
    }

    @Override
    protected Object parse(String rawData) {
        try {
            return objectMapper.readValue(rawData, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("JsonA: invalid JSON", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NormCaseOrder transform(Object parsed) {
        Map<String, Object> map = (Map<String, Object>) parsed;

        NormClient client = new NormClient();
        client.setFirstName(getString(map, "fname"));
        client.setLastName(getString(map, "lname"));
        client.setIdNumber(getString(map, "id_num"));

        NormAttorney attorney = new NormAttorney();
        attorney.setName(getString(map, "attorney_name"));
        attorney.setBarNumber(getString(map, "bar_num"));

        NormCaseInfo caseInfo = new NormCaseInfo();
        caseInfo.setCaseNumber(getString(map, "case_num"));
        caseInfo.setPrimaryCauseOfAction(getString(map, "cause"));
        caseInfo.setOpposingParty(getString(map, "opposing_party"));
        caseInfo.setLegalRemedySought(getString(map, "remedy"));
        caseInfo.setAdditionalCauses(getString(map, "additional_causes"));
        caseInfo.setPriorLegalActions(getString(map, "prior_actions"));
        caseInfo.setCaseDocuments(getString(map, "documents"));
        caseInfo.setReferringSource(getString(map, "referral"));

        NormCarePlan carePlan = new NormCarePlan();
        carePlan.setNotes(getString(map, "plan_notes"));
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
            throw new IllegalArgumentException("JsonA: client, attorney and caseInfo are required");
        }
        if (blank(order.getClient().getFirstName()) || blank(order.getClient().getLastName())) {
            throw new IllegalArgumentException("JsonA: client fname and lname are required");
        }
        if (blank(order.getAttorney().getBarNumber())) {
            throw new IllegalArgumentException("JsonA: bar_num is required");
        }
        if (blank(order.getCaseInfo().getCaseNumber())) {
            throw new IllegalArgumentException("JsonA: case_num is required");
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
