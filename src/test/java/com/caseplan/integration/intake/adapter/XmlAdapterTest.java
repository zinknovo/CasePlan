package com.caseplan.integration.intake.adapter;

import com.caseplan.integration.intake.model.NormCaseOrder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class XmlAdapterTest {

    private XmlAdapter adapter;

    @Before
    public void setup() {
        adapter = new XmlAdapter();
    }

    @Test
    public void getSourceName_returnsXml() {
        assertEquals("xml", adapter.getSourceName());
    }

    @Test
    public void process_validFullPayload_allFieldsMapped() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName>John</ClientFirstName>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<ClientIdNumber>ID123</ClientIdNumber>"
                + "<AttorneyName>Jane Smith</AttorneyName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "<CaseNumber>789012</CaseNumber>"
                + "<PrimaryCauseOfAction>Contract Breach</PrimaryCauseOfAction>"
                + "<OpposingParty>Acme Corp</OpposingParty>"
                + "<LegalRemedySought>Damages</LegalRemedySought>"
                + "<AdditionalCauses>Fraud</AdditionalCauses>"
                + "<PriorLegalActions>None</PriorLegalActions>"
                + "<CaseDocuments>doc1.pdf</CaseDocuments>"
                + "<ReferringSource>Court</ReferringSource>"
                + "<CarePlanNotes>Urgent</CarePlanNotes>"
                + "</CaseOrder>";

        NormCaseOrder order = adapter.process(xml);

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
        assertEquals(xml, order.getRawData());
    }

    @Test
    public void process_minimalValidPayload_nullOptionalFields() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName>John</ClientFirstName>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "<CaseNumber>789012</CaseNumber>"
                + "</CaseOrder>";

        NormCaseOrder order = adapter.process(xml);

        assertEquals("John", order.getClient().getFirstName());
        assertNull(order.getClient().getIdNumber());
        assertNull(order.getAttorney().getName());
        assertNull(order.getCaseInfo().getOpposingParty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_invalidXml_throwsException() {
        adapter.process("not valid xml <<>>");
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingClientFirstName_throwsException() {
        String xml = "<CaseOrder>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "<CaseNumber>789012</CaseNumber>"
                + "</CaseOrder>";
        adapter.process(xml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingBarNumber_throwsException() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName>John</ClientFirstName>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<CaseNumber>789012</CaseNumber>"
                + "</CaseOrder>";
        adapter.process(xml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingCaseNumber_throwsException() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName>John</ClientFirstName>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "</CaseOrder>";
        adapter.process(xml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyClientFirstName_throwsException() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName></ClientFirstName>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "<CaseNumber>789012</CaseNumber>"
                + "</CaseOrder>";
        adapter.process(xml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyClientLastName_throwsException() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName>John</ClientFirstName>"
                + "<ClientLastName></ClientLastName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "<CaseNumber>789012</CaseNumber>"
                + "</CaseOrder>";
        adapter.process(xml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyBarNumber_throwsException() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName>John</ClientFirstName>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<BarNumber></BarNumber>"
                + "<CaseNumber>789012</CaseNumber>"
                + "</CaseOrder>";
        adapter.process(xml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyCaseNumber_throwsException() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName>John</ClientFirstName>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "<CaseNumber></CaseNumber>"
                + "</CaseOrder>";
        adapter.process(xml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_missingClientLastName_throwsException() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName>John</ClientFirstName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "<CaseNumber>789012</CaseNumber>"
                + "</CaseOrder>";
        adapter.process(xml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void process_emptyAttorneyAndClient_throwsForClientFirst() {
        // Validates client name check when LastName is present but FirstName is empty
        String xml = "<CaseOrder>"
                + "<ClientFirstName>  </ClientFirstName>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "<CaseNumber>789012</CaseNumber>"
                + "</CaseOrder>";
        // Note: trim() makes "  " -> "" which is empty
        adapter.process(xml);
    }

    @Test
    public void process_emptyOptionalField_returnsEmptyString() {
        String xml = "<CaseOrder>"
                + "<ClientFirstName>John</ClientFirstName>"
                + "<ClientLastName>Doe</ClientLastName>"
                + "<BarNumber>BAR456</BarNumber>"
                + "<CaseNumber>789012</CaseNumber>"
                + "<OpposingParty></OpposingParty>"
                + "</CaseOrder>";
        NormCaseOrder order = adapter.process(xml);
        assertEquals("", order.getCaseInfo().getOpposingParty());
    }
}
