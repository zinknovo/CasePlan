package com.caseplan.repo;

import com.caseplan.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClientRepo extends JpaRepository<Client, Long> {
    Optional<Client> findByFirstNameAndLastName(String firstName, String lastName);
}
