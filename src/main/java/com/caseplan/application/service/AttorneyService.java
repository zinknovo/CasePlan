package com.caseplan.application.service;

import com.caseplan.adapter.out.persistence.AttorneyRepo;
import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.ValidationException;
import com.caseplan.domain.model.Attorney;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttorneyService {

    private final AttorneyRepo attorneyRepo;

    public CreateAttorneyResult create(String name, String barNumber) {
        String normalizedName = normalizeRequired(name, "Attorney name is required");
        String normalizedBarNumber = normalizeRequired(barNumber, "Bar number is required");

        Optional<Attorney> existingByBar = attorneyRepo.findByBarNumber(normalizedBarNumber);
        if (existingByBar.isPresent()) {
            Attorney existing = existingByBar.get();
            if (normalizedName.equals(existing.getName())) {
                return new CreateAttorneyResult(existing, false);
            }
            throw buildConflict(
                    "ATTORNEY_BAR_CONFLICT",
                    "Attorney barNumber already exists with a different name",
                    existing
            );
        }

        Optional<Attorney> existingByName = attorneyRepo.findByName(normalizedName);
        if (existingByName.isPresent()) {
            Attorney existing = existingByName.get();
            if (normalizedBarNumber.equals(existing.getBarNumber())) {
                return new CreateAttorneyResult(existing, false);
            }
            throw buildConflict(
                    "ATTORNEY_NAME_CONFLICT",
                    "Attorney name already exists with a different barNumber",
                    existing
            );
        }

        Attorney attorney = new Attorney();
        attorney.setName(normalizedName);
        attorney.setBarNumber(normalizedBarNumber);
        Attorney saved = attorneyRepo.save(attorney);
        return new CreateAttorneyResult(saved, true);
    }

    private String normalizeRequired(String value, String message) {
        if (value == null) {
            throw new ValidationException(message, null);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(message, null);
        }
        return trimmed;
    }

    private BlockException buildConflict(String code, String message, Attorney existing) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("existingAttorney", existing);
        return new BlockException(code, message, detail);
    }
}
