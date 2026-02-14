package com.caseplan.web.controller;

import com.caseplan.integration.intake.AdapterFactory;
import com.caseplan.integration.intake.model.NormCaseOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Intake API: receives raw data from different sources, uses AdapterFactory to pick adapter,
 * converts to NormCaseOrder. Business logic only sees NormCaseOrder; raw data is kept in order for storage/debug.
 */
@RestController
@RequestMapping("/api/intake")
public class IntakeController {

    private final AdapterFactory adapterFactory;

    public IntakeController(AdapterFactory adapterFactory) {
        this.adapterFactory = adapterFactory;
    }

    /**
     * POST /api/intake?source=jsonA|jsonB|xml
     * Body: raw JSON or XML string from the source.
     * Returns the normalized NormCaseOrder (including rawData for persistence/debug).
     */
    @PostMapping(consumes = "text/plain")
    public ResponseEntity<NormCaseOrder> intake(
            @RequestParam String source,
            @RequestBody String rawData) {

        return adapterFactory.getAdapter(source)
                .map(adapter -> {
                    NormCaseOrder order = adapter.process(rawData);
                    return ResponseEntity.status(HttpStatus.CREATED).body(order);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
