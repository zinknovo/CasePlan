package com.caseplan.integration.intake.adapter;

import com.caseplan.integration.intake.model.NormCaseOrder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonBAdapterTest {

    private JsonBAdapter adapter;

    @Before
    public void setup() {
        adapter = new JsonBAdapter();
    }

    @Test
    public void getSourceName_returnsJsonB() {
        assertEquals("jsonB", adapter.getSourceName());
    }

    @Test
    public void process_validFullPayload_allFieldsMapped() {
        String json = "{"
                + "\"ClientFirstName\":\"John\","
                + "\"ClientLastName\":\"Doe\","
                + "\"ClientIdNumber\":\"ID123\","
                + "\"AttorneyName\":\"Jane Smith\","
                + "\"BarNumber\":\"BAR456\","
                + "\"CaseNumber\":\"789012\","
                + "\"PrimaryCauseOfAction\":\"Contract Breach\","
                + "\"OpposingParty\":\"Acme Corp\","
                + "\"LegalRemedySought\":\"Damages\","
                + "\"AdditionalCauses\":\"Fraud\","
                + "\"PriorLegalActions\":\"None\","
                + "\"CaseDocuments\":\"doc1.pdf\","
                + "\"ReferringSource\":\"Court\","
                + "\"CarePlanNotes\":\"Urgent\""
                + "}";

        NormCaseOrder order = adapter.process(json);

        assertEquals("John", order.getClient().getFirstName());
        assertEquals("Doe", order.getClient().getLastName());
        assertEquals("ID123", order.getClient().getIdNumber());
        assertEquals("Jane Smith", order.getAttorney().getName());
        assertEquals("BAR456", order.getAttorney().getBarNumber());
        assertEquals("789012", order.getCaseInfo().getCaseNumber());
        assertEquals("Contract Breach", order.getCaseInfo().getPrimaryCauseOfAction());
        assertEquals("Acme Corp", order.getCaseInfo().getOpposingParty());
        assertEquals("Damages", order.getCaseInfo().getLegalRemedySought());
        assertEquals("Fraud", order.getCaseInfo().getAdditionalCauses());
        assertEquals("None", order.getCaseInfo().getPriorLegalActions());
        assertEquals("doc1.pdf", order.getCaseInfo().getCaseDocuments());
        assertEquals("Court", order.getCaseInfo().getReferringSource());
        assertEquals("Urgent", order.getCarePlan().getNotes());
        assertEquals("pending", order.getCarePlan().getStatus());
        assertEquals(json, order.getRawData());
    }

    @Test
    public void process_minimalValidPayload_nullOptionalFields() {
        String json = "{"
                + "\"ClientFirstName\":\"John\","
                + "\"ClientLastName\":\"Doe\","
                + "\"BarNumber\":\"BAR456\","
                + "\"CaseNumber\":\"789012\""
                + "}";

        NormCaseOrder order = adapter.process(json);

        assertEquals("John", order.getClient().getFirstName());
        assertNull(order.getClient().getIdNumber());
        assertNull(order.getAttorney().getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_invalidJson_throwsException() {
        adapter.process("not valid json");
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingClientFirstName_throwsException() {
        String json = "{\"ClientLastName\":\"Doe\",\"BarNumber\":\"BAR456\",\"CaseNumber\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingBarNumber_throwsException() {
        String json = "{\"ClientFirstName\":\"John\",\"ClientLastName\":\"Doe\",\"CaseNumber\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingCaseNumber_throwsException() {
        String json = "{\"ClientFirstName\":\"John\",\"ClientLastName\":\"Doe\",\"BarNumber\":\"BAR456\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyClientFirstName_throwsException() {
        String json = "{\"ClientFirstName\":\"\",\"ClientLastName\":\"Doe\",\"BarNumber\":\"BAR456\",\"CaseNumber\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyClientLastName_throwsException() {
        String json = "{\"ClientFirstName\":\"John\",\"ClientLastName\":\"\",\"BarNumber\":\"BAR456\",\"CaseNumber\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyBarNumber_throwsException() {
        String json = "{\"ClientFirstName\":\"John\",\"ClientLastName\":\"Doe\",\"BarNumber\":\"\",\"CaseNumber\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyCaseNumber_throwsException() {
        String json = "{\"ClientFirstName\":\"John\",\"ClientLastName\":\"Doe\",\"BarNumber\":\"BAR456\",\"CaseNumber\":\"\"}";
        adapter.process(json);
    }
}
