package com.caseplan.adapter.in.web.controller;

import com.caseplan.adapter.in.web.dto.CreateClientRequest;
import com.caseplan.adapter.in.web.dto.UpdateClientRequest;
import com.caseplan.adapter.in.web.response.PageResponseBuilder;
import com.caseplan.application.service.CasePlanService;
import com.caseplan.application.service.ClientService;
import com.caseplan.application.service.CreateClientResult;
import com.caseplan.domain.model.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import javax.validation.Valid;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final CasePlanService casePlanService;

    @GetMapping
    public Map<String, Object> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        var clientsPage = clientService.listPage(page, pageSize);
        return PageResponseBuilder.from(clientsPage, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Client> getById(@PathVariable Long id) {
        Optional<Client> optional = clientService.getById(id);
        if (optional.isPresent()) {
            return ResponseEntity.ok(optional.get());
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateClientRequest request) {
        CreateClientResult result = clientService.create(
                request.getFirstName(),
                request.getLastName(),
                request.getIdNumber()
        );

        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.getClient());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Client> update(@PathVariable Long id, @RequestBody @Valid UpdateClientRequest request) {
        Optional<Client> optional = clientService.update(id, request.getFirstName(), request.getLastName(), request.getIdNumber());
        if (optional.isPresent()) {
            return ResponseEntity.ok(optional.get());
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = clientService.delete(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().header(HttpHeaders.CONTENT_LENGTH, "0").build();
    }

    @GetMapping("/{id}/caseplans")
    public Map<String, Object> listClientCasePlans(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        var plansPage = casePlanService.listByClientIdPage(id, page, pageSize, status);
        return PageResponseBuilder.from(plansPage, page, pageSize);
    }
}
