package com.caseplan.adapter.in.web.controller;

import com.caseplan.adapter.in.web.dto.CreateClientRequest;
import com.caseplan.adapter.in.web.dto.UpdateClientRequest;
import com.caseplan.application.service.CasePlanService;
import com.caseplan.application.service.ClientService;
import com.caseplan.application.service.CreateClientResult;
import com.caseplan.common.exception.BlockException;
import com.caseplan.domain.model.CasePlan;
import com.caseplan.domain.model.Client;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ClientControllerTest {

    @Mock
    private ClientService clientService;
    @Mock
    private CasePlanService casePlanService;

    @InjectMocks
    private ClientController controller;

    @Test
    @SuppressWarnings("unchecked")
    public void listAll_withPagination_returnsPagedResponse() {
        Client c1 = new Client();
        c1.setId(1L);
        Client c2 = new Client();
        c2.setId(2L);
        Page<Client> page = new PageImpl<>(Arrays.asList(c1, c2), PageRequest.of(0, 20), 35);
        when(clientService.listPage(1, 20)).thenReturn(page);

        Map<String, Object> response = controller.listAll(1, 20);

        assertEquals(35L, response.get("count"));
        assertEquals(1, response.get("page"));
        assertEquals(20, response.get("page_size"));
        assertEquals(2, ((java.util.List<Client>) response.get("results")).size());
    }

    @Test
    public void getById_found_returns200() {
        Client client = new Client();
        client.setId(10L);
        when(clientService.getById(10L)).thenReturn(java.util.Optional.of(client));

        ResponseEntity<Client> response = controller.getById(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    public void getById_notFound_returns404() {
        when(clientService.getById(404L)).thenReturn(java.util.Optional.empty());

        ResponseEntity<Client> response = controller.getById(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void create_whenCreated_returns201() {
        CreateClientRequest request = new CreateClientRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setIdNumber("ID-1");

        Client client = new Client();
        client.setId(1L);
        client.setFirstName("John");
        client.setLastName("Doe");
        client.setIdNumber("ID-1");

        when(clientService.create("John", "Doe", "ID-1"))
                .thenReturn(new CreateClientResult(client, true));

        ResponseEntity<?> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    public void create_whenDuplicate_returns200() {
        CreateClientRequest request = new CreateClientRequest();
        request.setFirstName("John");
        request.setLastName("Doe");

        Client existing = new Client();
        existing.setId(2L);
        existing.setFirstName("John");
        existing.setLastName("Doe");

        when(clientService.create(anyString(), anyString(), isNull()))
                .thenReturn(new CreateClientResult(existing, false));

        ResponseEntity<?> response = controller.create(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test(expected = BlockException.class)
    public void create_whenConflict_propagates409Exception() {
        CreateClientRequest request = new CreateClientRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setIdNumber("ID-9");

        when(clientService.create("John", "Doe", "ID-9"))
                .thenThrow(new BlockException("CLIENT_ID_CONFLICT", "conflict", null));

        controller.create(request);
    }

    @Test
    public void update_found_returns200() {
        UpdateClientRequest request = new UpdateClientRequest();
        request.setFirstName("John");
        request.setLastName("Updated");
        request.setIdNumber("ID-2");

        Client updated = new Client();
        updated.setId(2L);
        when(clientService.update(2L, "John", "Updated", "ID-2")).thenReturn(java.util.Optional.of(updated));

        ResponseEntity<Client> response = controller.update(2L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    public void update_notFound_returns404() {
        UpdateClientRequest request = new UpdateClientRequest();
        request.setFirstName("John");
        request.setLastName("Updated");
        when(clientService.update(2L, "John", "Updated", null)).thenReturn(java.util.Optional.empty());

        ResponseEntity<Client> response = controller.update(2L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void delete_found_returns204() {
        when(clientService.delete(5L)).thenReturn(true);

        ResponseEntity<Void> response = controller.delete(5L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    public void delete_notFound_returns404() {
        when(clientService.delete(5L)).thenReturn(false);

        ResponseEntity<Void> response = controller.delete(5L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listClientCasePlans_withStatus_returnsPagedResponse() {
        CasePlan plan = new CasePlan();
        plan.setId(77L);
        Page<CasePlan> page = new PageImpl<>(Collections.singletonList(plan), PageRequest.of(0, 20), 1);
        when(casePlanService.listByClientIdPage(1L, 1, 20, "failed")).thenReturn(page);

        Map<String, Object> response = controller.listClientCasePlans(1L, 1, 20, "failed");

        assertEquals(1L, response.get("count"));
        assertEquals(1, response.get("page"));
        assertEquals(20, response.get("page_size"));
        assertTrue(response.get("results") instanceof java.util.List);
    }
}
