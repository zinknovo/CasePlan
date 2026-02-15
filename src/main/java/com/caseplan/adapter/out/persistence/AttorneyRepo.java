package com.caseplan.adapter.out.persistence;

import com.caseplan.domain.model.Attorney;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AttorneyRepo extends JpaRepository<Attorney, Long> {
    Optional<Attorney> findByBarNumber(String barNumber);
    Optional<Attorney> findByName(String name);
}
