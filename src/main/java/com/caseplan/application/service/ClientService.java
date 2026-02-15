package com.caseplan.application.service;

import com.caseplan.adapter.out.persistence.ClientRepo;
import com.caseplan.common.exception.BlockException;
import com.caseplan.common.exception.ValidationException;
import com.caseplan.domain.model.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepo clientRepo;

    public CreateClientResult create(String firstName, String lastName, String idNumber) {
        String normalizedFirstName = normalizeRequired(firstName, "Client first name is required");
        String normalizedLastName = normalizeRequired(lastName, "Client last name is required");
        String normalizedIdNumber = normalizeOptional(idNumber);

        if (normalizedIdNumber != null) {
            Optional<Client> existingById = clientRepo.findByIdNumber(normalizedIdNumber);
            if (existingById.isPresent()) {
                Client existing = existingById.get();
                if (nameMatches(existing, normalizedFirstName, normalizedLastName)) {
                    return new CreateClientResult(existing, false);
                }
                throw buildConflict(
                        "CLIENT_ID_CONFLICT",
                        "Client idNumber already exists with a different name",
                        existing
                );
            }
        }

        Optional<Client> existingByName = clientRepo.findByFirstNameAndLastName(normalizedFirstName, normalizedLastName);
        if (existingByName.isPresent()) {
            Client existing = existingByName.get();
            String existingIdNumber = normalizeOptional(existing.getIdNumber());

            if (normalizedIdNumber != null && existingIdNumber != null && !existingIdNumber.equals(normalizedIdNumber)) {
                throw buildConflict(
                        "CLIENT_NAME_CONFLICT",
                        "Client name already exists with a different idNumber",
                        existing
                );
            }

            if (normalizedIdNumber != null && existingIdNumber == null) {
                existing.setIdNumber(normalizedIdNumber);
                existing = clientRepo.save(existing);
            }

            return new CreateClientResult(existing, false);
        }

        Client client = new Client();
        client.setFirstName(normalizedFirstName);
        client.setLastName(normalizedLastName);
        client.setIdNumber(normalizedIdNumber);

        Client saved = clientRepo.save(client);
        return new CreateClientResult(saved, true);
    }

    public Page<Client> listPage(int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.max(pageSize, 1);
        Pageable pageable = PageRequest.of(
                normalizedPage - 1,
                normalizedPageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return clientRepo.findAll(pageable);
    }

    public Optional<Client> getById(Long id) {
        return clientRepo.findById(id);
    }

    public Optional<Client> update(Long id, String firstName, String lastName, String idNumber) {
        Optional<Client> optional = clientRepo.findById(id);
        if (optional.isEmpty()) {
            return Optional.empty();
        }

        String normalizedFirstName = normalizeRequired(firstName, "Client first name is required");
        String normalizedLastName = normalizeRequired(lastName, "Client last name is required");
        String normalizedIdNumber = normalizeOptional(idNumber);

        Client current = optional.get();

        if (normalizedIdNumber != null) {
            Optional<Client> existingById = clientRepo.findByIdNumber(normalizedIdNumber);
            if (existingById.isPresent() && !existingById.get().getId().equals(id)) {
                throw buildConflict(
                        "CLIENT_ID_CONFLICT",
                        "Client idNumber already exists for another client",
                        existingById.get()
                );
            }
        }

        Optional<Client> existingByName = clientRepo.findByFirstNameAndLastName(normalizedFirstName, normalizedLastName);
        if (existingByName.isPresent() && !existingByName.get().getId().equals(id)) {
            throw buildConflict(
                    "CLIENT_NAME_CONFLICT",
                    "Client name already exists for another client",
                    existingByName.get()
            );
        }

        current.setFirstName(normalizedFirstName);
        current.setLastName(normalizedLastName);
        current.setIdNumber(normalizedIdNumber);
        return Optional.of(clientRepo.save(current));
    }

    public boolean delete(Long id) {
        if (!clientRepo.existsById(id)) {
            return false;
        }
        clientRepo.deleteById(id);
        return true;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ValidationException(message, null);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean nameMatches(Client existing, String firstName, String lastName) {
        return firstName.equals(existing.getFirstName()) && lastName.equals(existing.getLastName());
    }

    private BlockException buildConflict(String code, String message, Client existing) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("existingClient", existing);
        return new BlockException(code, message, detail);
    }
}
