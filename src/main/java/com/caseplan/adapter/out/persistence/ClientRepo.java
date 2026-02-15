package com.caseplan.adapter.out.persistence;

import com.caseplan.domain.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepo extends JpaRepository<Client, Long> {
    Optional<Client> findByFirstNameAndLastName(String firstName, String lastName);
    Optional<Client> findByIdNumber(String idNumber);
    List<Client> findByFirstNameAndLastNameAndIdNumberNot(String firstName, String lastName, String idNumber);
}
