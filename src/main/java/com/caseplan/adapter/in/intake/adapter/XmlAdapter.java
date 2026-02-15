package com.caseplan.adapter.in.intake.adapter;

import com.caseplan.adapter.in.intake.model.NormAttorney;
import com.caseplan.adapter.in.intake.model.NormCarePlan;
import com.caseplan.adapter.in.intake.model.NormCaseInfo;
import com.caseplan.adapter.in.intake.model.NormCaseOrder;
import com.caseplan.adapter.in.intake.model.NormClient;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Adapter for XML intake format. Expects root element with child elements e.g. ClientFirstName, ClientLastName, BarNumber, CaseNumber, etc.
 */
@Component
public class XmlAdapter extends BaseIntakeAdapter {

    @Override
    public String getSourceName() {
        return "xml";
    }

    @Override
    protected Object parse(String rawData) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(rawData.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Xml: invalid XML", e);
        }
    }

    @Override
    protected NormCaseOrder transform(Object parsed) {
        Document doc = (Document) parsed;

        NormClient client = new NormClient();
        client.setFirstName(getText(doc, "ClientFirstName"));
        client.setLastName(getText(doc, "ClientLastName"));
        client.setIdNumber(getText(doc, "ClientIdNumber"));

        NormAttorney attorney = new NormAttorney();
        attorney.setName(getText(doc, "AttorneyName"));
        attorney.setBarNumber(getText(doc, "BarNumber"));

        NormCaseInfo caseInfo = new NormCaseInfo();
        caseInfo.setCaseNumber(getText(doc, "CaseNumber"));
        caseInfo.setPrimaryCauseOfAction(getText(doc, "PrimaryCauseOfAction"));
        caseInfo.setOpposingParty(getText(doc, "OpposingParty"));
        caseInfo.setRemedySought(getText(doc, "RemedySought"));
        caseInfo.setAdditionalCauses(getText(doc, "AdditionalCauses"));
        caseInfo.setPriorLegalActions(getText(doc, "PriorLegalActions"));
        caseInfo.setCaseDocuments(getText(doc, "CaseDocuments"));
        caseInfo.setReferringSource(getText(doc, "ReferringSource"));

        NormCarePlan carePlan = new NormCarePlan();
        carePlan.setNotes(getText(doc, "CarePlanNotes"));
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
            throw new IllegalArgumentException("Xml: client, attorney and caseInfo are required");
        }
        if (blank(order.getClient().getFirstName()) || blank(order.getClient().getLastName())) {
            throw new IllegalArgumentException("Xml: ClientFirstName and ClientLastName are required");
        }
        if (blank(order.getAttorney().getBarNumber())) {
            throw new IllegalArgumentException("Xml: BarNumber is required");
        }
        if (blank(order.getCaseInfo().getCaseNumber())) {
            throw new IllegalArgumentException("Xml: CaseNumber is required");
        }
    }

    private static String getText(Document doc, String tagName) {
        NodeList list = doc.getElementsByTagName(tagName);
        if (list.getLength() == 0) {
            return null;
        }
        String text = list.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private static boolean blank(String s) {
        return s == null || s.isEmpty();
    }
}
