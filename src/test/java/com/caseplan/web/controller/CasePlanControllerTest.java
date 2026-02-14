package com.caseplan.web.controller;

import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.WarningException;
import com.caseplan.core.entity.*;
import com.caseplan.core.repo.*;
import com.caseplan.core.service.CasePlanService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.caseplan.web.dto.CreateCasePlanRequest;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CasePlanControllerTest {

    @Mock private CasePlanService casePlanService;
    @Mock private CasePlanRepo casePlanRepo;
    @Mock private CaseInfoRepo caseInfoRepo;
    @Mock private ClientRepo clientRepo;
    @Mock private AttorneyRepo attorneyRepo;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOps;

    @InjectMocks
    private CasePlanController controller;

    @Before
    public void setup() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
    }

    private CreateCasePlanRequest buildRequest() {
        CreateCasePlanRequest req = new CreateCasePlanRequest();
        req.setClientFirstName("John");
        req.setClientLastName("Doe");
        req.setAttorneyName("Jane Smith");
        req.setBarNumber("BAR123");
        req.setCaseNumber("123456");
        req.setPrimaryCauseOfAction("Contract Breach");
        req.setLegalRemedySought("Damages");
        req.setOpposingParty("Acme Corp");
        return req;
    }

    private void mockSaves() {
        when(clientRepo.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(attorneyRepo.save(any(Attorney.class))).thenAnswer(inv -> {
            Attorney a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });
        when(caseInfoRepo.save(any(CaseInfo.class))).thenAnswer(inv -> {
            CaseInfo ci = inv.getArgument(0);
            ci.setId(1L);
            return ci;
        });
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(inv -> {
            CasePlan cp = inv.getArgument(0);
            cp.setId(1L);
            return cp;
        });
    }

    // ==================== GET endpoints ====================

    @Test
    public void listAll_delegatesToService() {
        CasePlan plan = new CasePlan();
        plan.setId(1L);
        when(casePlanService.listAll()).thenReturn(Collections.singletonList(plan));

        List<CasePlan> result = controller.listAll();

        assertEquals(1, result.size());
        verify(casePlanService).listAll();
    }

    @Test
    public void getById_found_returns200() {
        CasePlan plan = new CasePlan();
        plan.setId(1L);
        when(casePlanService.getById(1L)).thenReturn(Optional.of(plan));

        ResponseEntity<CasePlan> response = controller.getById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    public void getById_notFound_returns404() {
        when(casePlanService.getById(999L)).thenReturn(Optional.empty());

        ResponseEntity<CasePlan> response = controller.getById(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test(expected = NullPointerException.class)
    public void getById_nullId_throwsNullPointer() {
        controller.getById(null);
    }

    @Test
    public void getStatus_found_returns200() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "completed");
        when(casePlanService.getStatus(1L)).thenReturn(status);

        ResponseEntity<Map<String, Object>> response = controller.getStatus(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("completed", response.getBody().get("status"));
    }

    @Test
    public void getStatus_notFound_returns404() {
        when(casePlanService.getStatus(999L)).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.getStatus(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test(expected = NullPointerException.class)
    public void getStatus_nullId_throwsNullPointer() {
        controller.getStatus(null);
    }

    // ==================== POST create ====================

    @Test
    public void create_newClientNewAttorney_returns201() {
        CreateCasePlanRequest req = buildRequest();
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test(expected = BlockException.class)
    public void create_attorneyNameMismatch_throwsBlock() {
        CreateCasePlanRequest req = buildRequest();
        Attorney existing = new Attorney();
        existing.setId(1L);
        existing.setName("Different Name");
        existing.setBarNumber("BAR123");
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.of(existing));

        controller.create(req);
    }

    @Test(expected = WarningException.class)
    public void create_clientIdNameMismatch_noConfirm_throwsWarning() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("ID999");
        req.setConfirm(null);

        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        mockSaves();

        Client existing = new Client();
        existing.setId(1L);
        existing.setFirstName("Jane");
        existing.setLastName("Other");
        existing.setIdNumber("ID999");
        when(clientRepo.findByIdNumber("ID999")).thenReturn(Optional.of(existing));

        controller.create(req);
    }

    @Test
    public void create_clientIdNameMismatch_withConfirm_returns201WithWarnings() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("ID999");
        req.setConfirm(true);

        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        mockSaves();

        Client existing = new Client();
        existing.setId(1L);
        existing.setFirstName("Jane");
        existing.setLastName("Other");
        existing.setIdNumber("ID999");
        when(clientRepo.findByIdNumber("ID999")).thenReturn(Optional.of(existing));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body.get("warnings"));
    }

    @Test(expected = BlockException.class)
    public void create_duplicateCaseSameDay_throwsBlock() {
        CreateCasePlanRequest req = buildRequest();
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());

        CaseInfo existingCase = new CaseInfo();
        existingCase.setId(1L);
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), eq("Contract Breach"), eq("Acme Corp"), any(), any()))
                .thenReturn(Collections.singletonList(existingCase))  // same day
                .thenReturn(Collections.emptyList());  // different day

        controller.create(req);
    }

    @Test(expected = WarningException.class)
    public void create_similarCaseDifferentDay_noConfirm_throwsWarning() {
        CreateCasePlanRequest req = buildRequest();
        req.setConfirm(null);
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());

        CaseInfo existingCase = new CaseInfo();
        existingCase.setId(1L);
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList())          // same day check -> empty
                .thenReturn(Collections.singletonList(existingCase)); // different day check -> found

        controller.create(req);
    }

    @Test
    public void create_existingClientByName_reuses() {
        CreateCasePlanRequest req = buildRequest();
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());

        Client existing = new Client();
        existing.setId(99L);
        existing.setFirstName("John");
        existing.setLastName("Doe");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existing));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(clientRepo, never()).save(any(Client.class));
    }

    @Test
    public void create_existingAttorney_reuses() {
        CreateCasePlanRequest req = buildRequest();
        mockSaves();

        Attorney existing = new Attorney();
        existing.setId(88L);
        existing.setName("Jane Smith");
        existing.setBarNumber("BAR123");
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.of(existing));
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(attorneyRepo, never()).save(any(Attorney.class));
    }

    @Test(expected = WarningException.class)
    public void create_clientNameIdMismatch_noConfirm_throwsWarning() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("NEWID");
        req.setConfirm(null);
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByIdNumber("NEWID")).thenReturn(Optional.empty());

        Client existingByName = new Client();
        existingByName.setId(1L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber("OLDID");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));

        controller.create(req);
    }

    @Test
    public void create_clientFoundByIdNumber_matchingName_reuses() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("ID123");
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());

        Client existing = new Client();
        existing.setId(1L);
        existing.setFirstName("John");
        existing.setLastName("Doe");
        existing.setIdNumber("ID123");
        when(clientRepo.findByIdNumber("ID123")).thenReturn(Optional.of(existing));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(clientRepo, never()).save(any(Client.class));
    }

    @Test
    public void create_similarCaseDifferentDay_withConfirm_returns201WithWarnings() {
        CreateCasePlanRequest req = buildRequest();
        req.setConfirm(true);
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());

        CaseInfo existingCase = new CaseInfo();
        existingCase.setId(1L);
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.singletonList(existingCase));

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body.get("warnings"));
    }

    @Test
    public void create_noOpposingParty_usesEmptyString() {
        CreateCasePlanRequest req = buildRequest();
        req.setOpposingParty(null);
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), eq("Contract Breach"), eq(""), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    public void create_clientByNameWithIdNumber_existingHasNoId_reuses() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("NEWID");
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByIdNumber("NEWID")).thenReturn(Optional.empty());

        Client existingByName = new Client();
        existingByName.setId(1L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber(null); // no existing idNumber
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(clientRepo, never()).save(any(Client.class));
    }

    @Test
    public void create_clientByNameNoIdInRequest_reuses() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber(null); // no ID in request
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());

        Client existingByName = new Client();
        existingByName.setId(1L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber("EXISTING_ID");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    public void create_clientNameIdMismatch_withConfirm_returns201WithWarnings() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("NEWID");
        req.setConfirm(true);
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByIdNumber("NEWID")).thenReturn(Optional.empty());

        Client existingByName = new Client();
        existingByName.setId(1L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber("OLDID");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body.get("warnings"));
    }

    @Test
    public void create_emptyIdNumber_treatedAsNoId() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber(""); // empty string
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(clientRepo, never()).findByIdNumber(anyString());
    }

    @Test
    public void create_confirmFalse_sameAsNull_throwsWarningForIdMismatch() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("ID999");
        req.setConfirm(false);

        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        mockSaves();

        Client existing = new Client();
        existing.setId(1L);
        existing.setFirstName("Jane");
        existing.setLastName("Other");
        existing.setIdNumber("ID999");
        when(clientRepo.findByIdNumber("ID999")).thenReturn(Optional.of(existing));

        try {
            controller.create(req);
            fail("Expected WarningException");
        } catch (WarningException e) {
            assertEquals("CLIENT_ID_NAME_MISMATCH", e.getCode());
        }
    }

    @Test
    public void create_clientByNameMatchingId_reuses() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("SAME_ID");
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByIdNumber("SAME_ID")).thenReturn(Optional.empty());

        Client existingByName = new Client();
        existingByName.setId(1L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber("SAME_ID"); // same ID
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(clientRepo, never()).save(any(Client.class));
    }

    @Test(expected = WarningException.class)
    public void create_clientIdMatch_firstNameMatch_lastNameMismatch_throwsWarning() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("ID123");
        req.setConfirm(null);

        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        mockSaves();

        Client existing = new Client();
        existing.setId(1L);
        existing.setFirstName("John");      // matches
        existing.setLastName("Different");   // does NOT match
        existing.setIdNumber("ID123");
        when(clientRepo.findByIdNumber("ID123")).thenReturn(Optional.of(existing));

        controller.create(req);
    }

    @Test
    public void create_clientIdNumberProvided_notFoundById_foundByName_sameId_reuses() {
        CreateCasePlanRequest req = buildRequest();
        req.setClientIdNumber("ID123");
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByIdNumber("ID123")).thenReturn(Optional.empty());

        Client existingByName = new Client();
        existingByName.setId(1L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber("ID123"); // same id
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(clientRepo, never()).save(any(Client.class));
    }

    @Test
    public void create_noPrimaryCause_usesEmptyString() {
        CreateCasePlanRequest req = buildRequest();
        req.setPrimaryCauseOfAction(null);
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), eq(""), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    public void create_caseNumberNull_savedAsNull() {
        CreateCasePlanRequest req = buildRequest();
        req.setCaseNumber(null);
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<CaseInfo> caseInfoCaptor = ArgumentCaptor.forClass(CaseInfo.class);
        verify(caseInfoRepo).save(caseInfoCaptor.capture());
        assertNull(caseInfoCaptor.getValue().getCaseNumber());
    }

    @Test
    public void create_caseNumberBlank_savedAsNull() {
        CreateCasePlanRequest req = buildRequest();
        req.setCaseNumber("   ");
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<CaseInfo> caseInfoCaptor = ArgumentCaptor.forClass(CaseInfo.class);
        verify(caseInfoRepo).save(caseInfoCaptor.capture());
        assertNull(caseInfoCaptor.getValue().getCaseNumber());
    }

    @Test
    public void create_whenPlanIdNull_doesNotPushQueue() {
        CreateCasePlanRequest req = buildRequest();
        mockSaves();
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(listOps, never()).rightPush(anyString(), anyString());
    }
}
