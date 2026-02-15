package com.caseplan.application.service;

import com.caseplan.application.port.in.CreateCasePlanCommand;
import com.caseplan.domain.model.*;
import com.caseplan.application.port.out.QueuePort;
import com.caseplan.adapter.out.persistence.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CasePlanServiceTest {

    @Mock private ClientRepo clientRepo;
    @Mock private AttorneyRepo attorneyRepo;
    @Mock private CaseInfoRepo caseInfoRepo;
    @Mock private CasePlanRepo casePlanRepo;
    @Mock private QueuePort queuePort;

    @InjectMocks
    private CasePlanService service;

    private CreateCasePlanCommand request;

    @Before
    public void setup() {
        // 构造一个通用的请求对象
        request = new CreateCasePlanCommand();
        request.setClientFirstName("John");
        request.setClientLastName("Doe");
        request.setAttorneyName("Jane Smith");
        request.setBarNumber("BAR123");
        request.setCaseNumber("123456");
        request.setPrimaryCauseOfAction("Contract Breach");
        request.setRemedySought("Damages");

        // mock save 方法：返回传入的对象，并模拟 id 生成
        when(clientRepo.save(any(Client.class))).thenAnswer(new Answer<Client>() {
            @Override
            public Client answer(InvocationOnMock invocation) {
                Client client = invocation.getArgument(0);
                client.setId(1L);
                return client;
            }
        });
        when(attorneyRepo.save(any(Attorney.class))).thenAnswer(new Answer<Attorney>() {
            @Override
            public Attorney answer(InvocationOnMock invocation) {
                Attorney attorney = invocation.getArgument(0);
                attorney.setId(1L);
                return attorney;
            }
        });
        when(caseInfoRepo.save(any(CaseInfo.class))).thenAnswer(new Answer<CaseInfo>() {
            @Override
            public CaseInfo answer(InvocationOnMock invocation) {
                CaseInfo caseInfo = invocation.getArgument(0);
                caseInfo.setId(1L);
                return caseInfo;
            }
        });
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(new Answer<CasePlan>() {
            @Override
            public CasePlan answer(InvocationOnMock invocation) {
                CasePlan casePlan = invocation.getArgument(0);
                casePlan.setId(1L);
                return casePlan;
            }
        });
    }

    // ==================== createCasePlan ====================

    @Test
    public void createCasePlan_newClientNewAttorney_saveBoth() {
        when(clientRepo.findByFirstNameAndLastName("John", "Doe"))
                .thenReturn(Optional.empty());
        when(attorneyRepo.findByBarNumber("BAR123"))
                .thenReturn(Optional.empty());

        CasePlan result = service.createCasePlan(request);

        // 新 Client 和 Attorney 都应该被 save
        verify(clientRepo).save(any(Client.class));
        verify(attorneyRepo).save(any(Attorney.class));

        // CaseInfo 和 CasePlan 也应该被 save
        verify(caseInfoRepo).save(any(CaseInfo.class));
        verify(casePlanRepo).save(any(CasePlan.class));

        // 应该推到队列
        verify(queuePort).enqueue("1");

        assertEquals("pending", result.getStatus());
    }

    @Test
    public void createCasePlan_existingClient_noSaveClient() {
        Client existingClient = new Client();
        existingClient.setId(99L);
        existingClient.setFirstName("John");
        existingClient.setLastName("Doe");

        when(clientRepo.findByFirstNameAndLastName("John", "Doe"))
                .thenReturn(Optional.of(existingClient));
        when(attorneyRepo.findByBarNumber("BAR123"))
                .thenReturn(Optional.empty());

        service.createCasePlan(request);

        // Client 已存在，不应该调 save
        verify(clientRepo, never()).save(any(Client.class));

        // 验证 CaseInfo 绑定的是已有的 Client
        ArgumentCaptor<CaseInfo> captor = ArgumentCaptor.forClass(CaseInfo.class);
        verify(caseInfoRepo).save(captor.capture());
        assertEquals(Long.valueOf(99L), captor.getValue().getClient().getId());
    }

    @Test
    public void createCasePlan_existingAttorney_noSaveAttorney() {
        Attorney existingAttorney = new Attorney();
        existingAttorney.setId(88L);
        existingAttorney.setName("Jane Smith");
        existingAttorney.setBarNumber("BAR123");

        when(clientRepo.findByFirstNameAndLastName("John", "Doe"))
                .thenReturn(Optional.empty());
        when(attorneyRepo.findByBarNumber("BAR123"))
                .thenReturn(Optional.of(existingAttorney));

        service.createCasePlan(request);

        // Attorney 已存在，不应该调 save
        verify(attorneyRepo, never()).save(any(Attorney.class));

        // 验证 CaseInfo 绑定的是已有的 Attorney
        ArgumentCaptor<CaseInfo> captor = ArgumentCaptor.forClass(CaseInfo.class);
        verify(caseInfoRepo).save(captor.capture());
        assertEquals(Long.valueOf(88L), captor.getValue().getAttorney().getId());
    }

    @Test
    public void createCasePlan_caseInfoFieldsMappedCorrectly() {
        request.setAdditionalCauses("Fraud");
        request.setPriorLegalActions("None");
        request.setCaseDocuments("doc1.pdf");
        request.setReferringSource("Court");

        when(clientRepo.findByFirstNameAndLastName("John", "Doe"))
                .thenReturn(Optional.empty());
        when(attorneyRepo.findByBarNumber("BAR123"))
                .thenReturn(Optional.empty());

        service.createCasePlan(request);

        ArgumentCaptor<CaseInfo> captor = ArgumentCaptor.forClass(CaseInfo.class);
        verify(caseInfoRepo).save(captor.capture());
        CaseInfo saved = captor.getValue();

        assertEquals("123456", saved.getCaseNumber());
        assertEquals("Contract Breach", saved.getPrimaryCauseOfAction());
        assertEquals("Damages", saved.getRemedySought());
        assertEquals("Fraud", saved.getAdditionalCauses());
        assertEquals("None", saved.getPriorLegalActions());
        assertEquals("doc1.pdf", saved.getCaseDocuments());
        assertEquals("Court", saved.getReferringSource());
    }

    // ==================== getStatus ====================

    @Test
    public void getStatus_completed_returnsStatusAndContent() {
        CasePlan plan = new CasePlan();
        plan.setStatus("completed");
        plan.setGeneratedPlan("This is the plan.");

        when(casePlanRepo.findById(1L)).thenReturn(Optional.of(plan));

        Map<String, Object> result = service.getStatus(1L);

        assertEquals("completed", result.get("status"));
        assertEquals("This is the plan.", result.get("content"));
        assertNull(result.get("error"));
    }

    @Test
    public void getStatus_failed_returnsStatusAndError() {
        CasePlan plan = new CasePlan();
        plan.setStatus("failed");
        plan.setErrorMessage("LLM timeout");

        when(casePlanRepo.findById(1L)).thenReturn(Optional.of(plan));

        Map<String, Object> result = service.getStatus(1L);

        assertEquals("failed", result.get("status"));
        assertEquals("LLM timeout", result.get("error"));
        assertNull(result.get("content"));
    }

    @Test
    public void getStatus_pending_returnsStatusOnly() {
        CasePlan plan = new CasePlan();
        plan.setStatus("pending");

        when(casePlanRepo.findById(1L)).thenReturn(Optional.of(plan));

        Map<String, Object> result = service.getStatus(1L);

        assertEquals("pending", result.get("status"));
        assertNull(result.get("content"));
        assertNull(result.get("error"));
    }

    @Test
    public void getStatus_notFound_returnsNull() {
        when(casePlanRepo.findById(999L)).thenReturn(Optional.empty());

        Map<String, Object> result = service.getStatus(999L);

        assertNull(result);
    }

    // ==================== additional branches ====================

    @Test(expected = com.caseplan.common.exception.BlockException.class)
    public void create_attorneyNameMismatch_throwsBlockException() {
        Attorney existing = new Attorney();
        existing.setName("Other");
        existing.setBarNumber("BAR123");
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.of(existing));

        service.create(request);
    }

    @Test(expected = com.caseplan.common.exception.WarningException.class)
    public void create_clientIdNameMismatch_withoutConfirm_throwsWarning() {
        request.setClientIdNumber("ID-1");
        request.setConfirm(false);

        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        Client existing = new Client();
        existing.setId(7L);
        existing.setFirstName("Alice");
        existing.setLastName("Bob");
        existing.setIdNumber("ID-1");
        when(clientRepo.findByIdNumber("ID-1")).thenReturn(Optional.of(existing));

        service.create(request);
    }

    @Test
    public void create_clientIdNameMismatch_withConfirm_addsWarning() {
        request.setClientIdNumber("ID-1");
        request.setConfirm(true);

        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        Client existing = new Client();
        existing.setId(7L);
        existing.setFirstName("Alice");
        existing.setLastName("Bob");
        existing.setIdNumber("ID-1");
        when(clientRepo.findByIdNumber("ID-1")).thenReturn(Optional.of(existing));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Collections.emptyList());

        CreateCasePlanResult result = service.create(request);
        assertEquals(1, result.getWarnings().size());
    }

    @Test
    public void create_clientIdMatchedName_usesExistingClient() {
        request.setClientIdNumber("ID-2");
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        Client existing = new Client();
        existing.setId(9L);
        existing.setFirstName("John");
        existing.setLastName("Doe");
        existing.setIdNumber("ID-2");
        when(clientRepo.findByIdNumber("ID-2")).thenReturn(Optional.of(existing));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Collections.emptyList());

        service.create(request);
        verify(clientRepo, never()).save(any(Client.class));
    }

    @Test
    public void create_similarCaseWithConfirm_trueReturnsWarnings() {
        request.setConfirm(true);
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Arrays.asList(new CaseInfo()));

        CreateCasePlanResult result = service.create(request);
        assertEquals(1, result.getWarnings().size());
    }

    @Test(expected = com.caseplan.common.exception.BlockException.class)
    public void create_duplicateCaseSameDay_throwsBlockException() {
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Arrays.asList(new CaseInfo()));

        service.create(request);
    }

    @Test(expected = com.caseplan.common.exception.WarningException.class)
    public void create_similarCaseWithoutConfirm_throwsWarning() {
        request.setConfirm(false);
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Arrays.asList(new CaseInfo()));

        service.create(request);
    }

    @Test(expected = com.caseplan.common.exception.WarningException.class)
    public void create_nameMatchButDifferentClientId_withoutConfirm_throwsWarning() {
        request.setClientIdNumber("NEW-ID");
        request.setConfirm(false);
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByIdNumber("NEW-ID")).thenReturn(Optional.empty());
        Client existingByName = new Client();
        existingByName.setId(123L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber("OLD-ID");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));

        service.create(request);
    }

    @Test
    public void create_nameMatchButDifferentClientId_withConfirm_addsWarning() {
        request.setClientIdNumber("NEW-ID");
        request.setConfirm(true);
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByIdNumber("NEW-ID")).thenReturn(Optional.empty());
        Client existingByName = new Client();
        existingByName.setId(123L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber("OLD-ID");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Collections.emptyList());

        CreateCasePlanResult result = service.create(request);
        assertEquals(1, result.getWarnings().size());
    }

    @Test(expected = com.caseplan.common.exception.WarningException.class)
    public void create_nameMatchDifferentId_confirmNull_throwsWarning() {
        request.setClientIdNumber("NEW-ID");
        request.setConfirm(null);
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByIdNumber("NEW-ID")).thenReturn(Optional.empty());
        Client existingByName = new Client();
        existingByName.setId(123L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber("OLD-ID");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));

        service.create(request);
    }

    @Test
    public void create_existingClientByName_whenCommandClientIdNull_returnsExistingWithoutWarning() {
        request.setClientIdNumber(null);
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        Client existingByName = new Client();
        existingByName.setId(77L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber("EXISTING");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Collections.emptyList());

        CreateCasePlanResult result = service.create(request);
        assertEquals(0, result.getWarnings().size());
    }

    @Test
    public void create_caseNumberBlank_storedAsNull() {
        request.setCaseNumber("   ");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Collections.emptyList());

        service.create(request);
        ArgumentCaptor<CaseInfo> captor = ArgumentCaptor.forClass(CaseInfo.class);
        verify(caseInfoRepo, atLeastOnce()).save(captor.capture());
        assertNull(captor.getValue().getCaseNumber());
    }

    @Test
    public void create_whenCasePlanIdNull_doesNotEnqueue() {
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(new Answer<CasePlan>() {
            @Override
            public CasePlan answer(InvocationOnMock invocation) {
                return invocation.getArgument(0);
            }
        }); // keep id null

        CasePlan plan = service.createCasePlan(request);

        assertEquals("pending", plan.getStatus());
        verify(queuePort, never()).enqueue(anyString());
    }

    @Test
    public void listAll_and_getById_delegatesRepo() {
        service.listAll();
        service.getById(1L);
        verify(casePlanRepo).findAllByOrderByCreatedAtDesc();
        verify(casePlanRepo).findById(1L);
    }

    @Test
    public void listPage_delegatesRepoWithNormalizedParams() {
        when(casePlanRepo.search(any(), any(), any())).thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 1), 0));

        service.listPage(0, 0, " completed ", " zhang ");

        verify(casePlanRepo).search(eq("completed"), eq("zhang"), any());
    }

    @Test
    public void create_existingAttorneySameName_andExistingClientByName_noWarnings() {
        Attorney existingAttorney = new Attorney();
        existingAttorney.setName("Jane Smith");
        existingAttorney.setBarNumber("BAR123");
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.of(existingAttorney));

        Client existingClient = new Client();
        existingClient.setId(42L);
        existingClient.setFirstName("John");
        existingClient.setLastName("Doe");
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingClient));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Collections.emptyList());

        CreateCasePlanResult result = service.create(request);
        assertEquals(0, result.getWarnings().size());
        verify(attorneyRepo, never()).save(any(Attorney.class));
        verify(clientRepo, never()).save(any(Client.class));
    }

    @Test
    public void create_emptyClientId_treatedAsNoClientIdPath() {
        request.setClientIdNumber("");
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Collections.emptyList());

        service.create(request);
        verify(clientRepo, never()).findByIdNumber(anyString());
    }

    @Test
    public void create_nullCauseAndOpposingParty_usesEmptyForDuplicateCheck() {
        request.setPrimaryCauseOfAction(null);
        request.setOpposingParty(null);
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.empty());
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Collections.emptyList());

        service.create(request);
        verify(caseInfoRepo, atLeastOnce()).findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), eq(""), eq(""), any(), any());
    }

    @Test
    public void listByClientIdPage_delegatesRepoWithNormalizedStatus() {
        when(casePlanRepo.findByClientIdAndStatus(anyLong(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 1), 0));

        service.listByClientIdPage(1L, 0, 0, " failed ");

        verify(casePlanRepo).findByClientIdAndStatus(eq(1L), eq("failed"), any());
    }

    @Test
    public void retryFailed_failedCase_resetsAndEnqueues() {
        CasePlan failed = new CasePlan();
        failed.setId(10L);
        failed.setStatus("failed");
        failed.setErrorMessage("boom");
        failed.setGeneratedPlan("old");

        when(casePlanRepo.findById(10L)).thenReturn(Optional.of(failed));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(new Answer<CasePlan>() {
            @Override
            public CasePlan answer(InvocationOnMock invocation) {
                return invocation.getArgument(0);
            }
        });

        Optional<CasePlan> result = service.retryFailed(10L);

        assertTrue(result.isPresent());
        assertEquals("pending", result.get().getStatus());
        assertNull(result.get().getErrorMessage());
        assertNull(result.get().getGeneratedPlan());
        verify(queuePort).enqueue("10");
    }

    @Test(expected = com.caseplan.common.exception.BlockException.class)
    public void retryFailed_nonFailedCase_throwsBlock() {
        CasePlan pending = new CasePlan();
        pending.setId(11L);
        pending.setStatus("pending");
        when(casePlanRepo.findById(11L)).thenReturn(Optional.of(pending));

        service.retryFailed(11L);
    }

    @Test
    public void retryFailed_notFound_returnsEmpty() {
        when(casePlanRepo.findById(12L)).thenReturn(Optional.empty());

        Optional<CasePlan> result = service.retryFailed(12L);

        assertFalse(result.isPresent());
    }

    @Test
    public void getForDownload_completedWithContent_returnsCasePlan() {
        CasePlan completed = new CasePlan();
        completed.setId(20L);
        completed.setStatus("completed");
        completed.setGeneratedPlan("content");
        when(casePlanRepo.findById(20L)).thenReturn(Optional.of(completed));

        Optional<CasePlan> result = service.getForDownload(20L);

        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(20L), result.get().getId());
    }

    @Test(expected = com.caseplan.common.exception.BlockException.class)
    public void getForDownload_notReady_throwsBlock() {
        CasePlan failed = new CasePlan();
        failed.setId(21L);
        failed.setStatus("failed");
        failed.setGeneratedPlan(null);
        when(casePlanRepo.findById(21L)).thenReturn(Optional.of(failed));

        service.getForDownload(21L);
    }

    @Test
    public void getForDownload_notFound_returnsEmpty() {
        when(casePlanRepo.findById(22L)).thenReturn(Optional.empty());

        Optional<CasePlan> result = service.getForDownload(22L);

        assertFalse(result.isPresent());
    }

    @Test(expected = com.caseplan.common.exception.BlockException.class)
    public void getForDownload_completedButWhitespaceContent_throwsBlock() {
        CasePlan plan = new CasePlan();
        plan.setId(30L);
        plan.setStatus("completed");
        plan.setGeneratedPlan("   ");
        when(casePlanRepo.findById(30L)).thenReturn(Optional.of(plan));

        service.getForDownload(30L);
    }

    @Test(expected = com.caseplan.common.exception.BlockException.class)
    public void getForDownload_completedButNullContent_throwsBlock() {
        CasePlan plan = new CasePlan();
        plan.setId(31L);
        plan.setStatus("completed");
        plan.setGeneratedPlan(null);
        when(casePlanRepo.findById(31L)).thenReturn(Optional.of(plan));

        service.getForDownload(31L);
    }

    @Test
    public void create_existingClientByName_withNullIdNumberOnExisting_noWarning() {
        request.setClientIdNumber("NEW-ID");
        request.setConfirm(false);
        when(attorneyRepo.findByBarNumber("BAR123")).thenReturn(Optional.empty());
        when(clientRepo.findByIdNumber("NEW-ID")).thenReturn(Optional.empty());
        Client existingByName = new Client();
        existingByName.setId(55L);
        existingByName.setFirstName("John");
        existingByName.setLastName("Doe");
        existingByName.setIdNumber(null); // existing client has no idNumber
        when(clientRepo.findByFirstNameAndLastName("John", "Doe")).thenReturn(Optional.of(existingByName));
        when(caseInfoRepo.findByClientIdAndPrimaryCauseOfActionAndOpposingPartyAndCreatedAtBetween(
                anyLong(), anyString(), anyString(), any(), any())).thenReturn(
                Collections.emptyList(), Collections.emptyList());

        CreateCasePlanResult result = service.create(request);
        // existing client has null idNumber, so the ID mismatch branch is not entered
        assertEquals(0, result.getWarnings().size());
    }

    @Test
    public void retryFailed_savedIdNull_doesNotEnqueue() {
        CasePlan failed = new CasePlan();
        failed.setId(40L);
        failed.setStatus("failed");
        when(casePlanRepo.findById(40L)).thenReturn(Optional.of(failed));
        when(casePlanRepo.save(any(CasePlan.class))).thenAnswer(new Answer<CasePlan>() {
            @Override
            public CasePlan answer(InvocationOnMock invocation) {
                CasePlan saved = invocation.getArgument(0);
                saved.setId(null); // simulate null ID after save
                return saved;
            }
        });

        Optional<CasePlan> result = service.retryFailed(40L);

        assertTrue(result.isPresent());
        verify(queuePort, never()).enqueue(anyString());
    }
}
