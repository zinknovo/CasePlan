package com.caseplan.adapter.in.intake.adapter;

import com.caseplan.adapter.in.intake.model.NormCaseOrder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonAAdapterTest {

    private JsonAAdapter adapter;

    @Before
    public void setup() {
        adapter = new JsonAAdapter();
    }

    @Test
    public void getSourceName_returnsJsonA() {
        assertEquals("jsonA", adapter.getSourceName());
    }

    @Test
    public void process_validFullPayload_allFieldsMapped() {
        String json = "{"
                + "\"fname\":\"John\","
                + "\"lname\":\"Doe\","
                + "\"id_num\":\"ID123\","
                + "\"attorney_name\":\"Jane Smith\","
                + "\"bar_num\":\"BAR456\","
                + "\"case_num\":\"789012\","
                + "\"cause\":\"Contract Breach\","
                + "\"opposing_party\":\"Acme Corp\","
                + "\"remedy\":\"Damages\","
                + "\"additional_causes\":\"Fraud\","
                + "\"prior_actions\":\"None\","
                + "\"documents\":\"doc1.pdf\","
                + "\"referral\":\"Court\","
                + "\"plan_notes\":\"Urgent\""
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
        assertEquals("Damages", order.getCaseInfo().getRemedySought());
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
                + "\"fname\":\"John\","
                + "\"lname\":\"Doe\","
                + "\"bar_num\":\"BAR456\","
                + "\"case_num\":\"789012\""
                + "}";

        NormCaseOrder order = adapter.process(json);

        assertEquals("John", order.getClient().getFirstName());
        assertEquals("Doe", order.getClient().getLastName());
        assertNull(order.getClient().getIdNumber());
        assertNull(order.getAttorney().getName());
        assertEquals("BAR456", order.getAttorney().getBarNumber());
        assertEquals("789012", order.getCaseInfo().getCaseNumber());
        assertNull(order.getCaseInfo().getPrimaryCauseOfAction());
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_invalidJson_throwsException() {
        adapter.process("not valid json");
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingFname_throwsException() {
        String json = "{\"lname\":\"Doe\",\"bar_num\":\"BAR456\",\"case_num\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingLname_throwsException() {
        String json = "{\"fname\":\"John\",\"bar_num\":\"BAR456\",\"case_num\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingBarNum_throwsException() {
        String json = "{\"fname\":\"John\",\"lname\":\"Doe\",\"case_num\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingCaseNum_throwsException() {
        String json = "{\"fname\":\"John\",\"lname\":\"Doe\",\"bar_num\":\"BAR456\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyFname_throwsException() {
        String json = "{\"fname\":\"\",\"lname\":\"Doe\",\"bar_num\":\"BAR456\",\"case_num\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyBarNum_throwsException() {
        String json = "{\"fname\":\"John\",\"lname\":\"Doe\",\"bar_num\":\"\",\"case_num\":\"789012\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyCaseNum_throwsException() {
        String json = "{\"fname\":\"John\",\"lname\":\"Doe\",\"bar_num\":\"BAR456\",\"case_num\":\"\"}";
        adapter.process(json);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyLname_throwsException() {
        String json = "{\"fname\":\"John\",\"lname\":\"\",\"bar_num\":\"BAR456\",\"case_num\":\"789012\"}";
        adapter.process(json);
    }
}
