package com.caseplan.core.service;

import com.caseplan.core.entity.*;
import com.caseplan.core.repo.*;
import com.caseplan.web.dto.CreateCasePlanRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CasePlanServiceTest {

    @Mock private ClientRepo clientRepo;
    @Mock private AttorneyRepo attorneyRepo;
    @Mock private CaseInfoRepo caseInfoRepo;
    @Mock private CasePlanRepo casePlanRepo;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOps;

    @InjectMocks
    private CasePlanService service;

    private CreateCasePlanRequest request;

    @Before
    public void setup() {
        when(redisTemplate.opsForList()).thenReturn(listOps);

        // 构造一个通用的请求对象
        request = new CreateCasePlanRequest();
        request.setClientFirstName("John");
        request.setClientLastName("Doe");
        request.setAttorneyName("Jane Smith");
        request.setBarNumber("BAR123");
        request.setCaseNumber("123456");
        request.setPrimaryCauseOfAction("Contract Breach");
        request.setLegalRemedySought("Damages");

        // mock save 方法：返回传入的对象，并模拟 id 生成
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

        // 应该推到 Redis 队列
        verify(listOps).rightPush("caseplan:pending", "1");

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
        assertEquals("Damages", saved.getLegalRemedySought());
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
}
