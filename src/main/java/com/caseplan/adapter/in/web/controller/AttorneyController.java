package com.caseplan.adapter.in.web.controller;

import com.caseplan.adapter.in.web.dto.CreateAttorneyRequest;
import com.caseplan.application.service.AttorneyService;
import com.caseplan.application.service.CreateAttorneyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/attorneys")
@RequiredArgsConstructor
public class AttorneyController {

    private final AttorneyService attorneyService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateAttorneyRequest request) {
        CreateAttorneyResult result = attorneyService.create(request.getName(), request.getBarNumber());
        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.getAttorney());
    }
}
