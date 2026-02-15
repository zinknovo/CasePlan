package com.caseplan.adapter.in.web.controller;

import com.caseplan.adapter.in.intake.AdapterFactory;
import com.caseplan.adapter.in.intake.adapter.BaseIntakeAdapter;
import com.caseplan.adapter.in.intake.model.NormCaseOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

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

        Optional<BaseIntakeAdapter> optionalAdapter = adapterFactory.getAdapter(source);
        if (optionalAdapter.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        NormCaseOrder order = optionalAdapter.get().process(rawData);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
