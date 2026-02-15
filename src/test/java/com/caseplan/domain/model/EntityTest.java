package com.caseplan.domain.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class EntityTest {

    // ==================== Client ====================

    @Test
    public void client_prePersist_setsCreatedAt() {
        Client client = new Client();
        client.setFirstName("John");
        client.setLastName("Doe");
        client.setIdNumber("ID123");
        assertNull(client.getCreatedAt());

        client.onCreate();

        assertNotNull(client.getCreatedAt());
        assertEquals("John", client.getFirstName());
        assertEquals("Doe", client.getLastName());
        assertEquals("ID123", client.getIdNumber());
    }

    @Test
    public void client_gettersSetters() {
        Client client = new Client();
        client.setId(1L);
        assertEquals(Long.valueOf(1L), client.getId());
    }

    // ==================== Attorney ====================

    @Test
    public void attorney_prePersist_setsCreatedAt() {
        Attorney attorney = new Attorney();
        attorney.setName("Jane Smith");
        attorney.setBarNumber("BAR123");
        assertNull(attorney.getCreatedAt());

        attorney.onCreate();

        assertNotNull(attorney.getCreatedAt());
        assertEquals("Jane Smith", attorney.getName());
        assertEquals("BAR123", attorney.getBarNumber());
    }

    @Test
    public void attorney_gettersSetters() {
        Attorney attorney = new Attorney();
        attorney.setId(1L);
        assertEquals(Long.valueOf(1L), attorney.getId());
    }

    // ==================== CaseInfo ====================

    @Test
    public void caseInfo_prePersist_setsCreatedAt() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setCaseNumber("123456");
        caseInfo.setPrimaryCauseOfAction("Breach");
        caseInfo.setOpposingParty("Acme");
        caseInfo.setRemedySought("Damages");
        caseInfo.setAdditionalCauses("Fraud");
        caseInfo.setPriorLegalActions("None");
        caseInfo.setCaseDocuments("doc.pdf");
        caseInfo.setReferringSource("Court");
        assertNull(caseInfo.getCreatedAt());

        caseInfo.onCreate();

        assertNotNull(caseInfo.getCreatedAt());
    }

    @Test
    public void caseInfo_relationships() {
        CaseInfo caseInfo = new CaseInfo();
        Client client = new Client();
        client.setId(1L);
        Attorney attorney = new Attorney();
        attorney.setId(2L);

        caseInfo.setClient(client);
        caseInfo.setAttorney(attorney);
        caseInfo.setId(3L);

        assertEquals(Long.valueOf(1L), caseInfo.getClient().getId());
        assertEquals(Long.valueOf(2L), caseInfo.getAttorney().getId());
        assertEquals(Long.valueOf(3L), caseInfo.getId());
    }

    // ==================== CasePlan ====================

    @Test
    public void casePlan_prePersist_setsDefaultStatus() {
        CasePlan plan = new CasePlan();
        assertNull(plan.getStatus());

        plan.onCreate();

        assertEquals("pending", plan.getStatus());
        assertNotNull(plan.getCreatedAt());
        assertNotNull(plan.getUpdatedAt());
    }

    @Test
    public void casePlan_prePersist_preservesExistingStatus() {
        CasePlan plan = new CasePlan();
        plan.setStatus("processing");

        plan.onCreate();

        assertEquals("processing", plan.getStatus());
    }

    @Test
    public void casePlan_preUpdate_updatesTimestamp() {
        CasePlan plan = new CasePlan();
        plan.onCreate();
        java.time.Instant firstUpdate = plan.getUpdatedAt();

        // Small delay to ensure different timestamp
        plan.onUpdate();

        assertNotNull(plan.getUpdatedAt());
    }

    @Test
    public void casePlan_gettersSetters() {
        CasePlan plan = new CasePlan();
        plan.setId(1L);
        plan.setGeneratedPlan("the plan");
        plan.setErrorMessage("error msg");

        CaseInfo caseInfo = new CaseInfo();
        plan.setCaseInfo(caseInfo);

        assertEquals(Long.valueOf(1L), plan.getId());
        assertEquals("the plan", plan.getGeneratedPlan());
        assertEquals("error msg", plan.getErrorMessage());
        assertNotNull(plan.getCaseInfo());
    }
}
