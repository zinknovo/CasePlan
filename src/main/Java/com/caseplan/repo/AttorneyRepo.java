package com.caseplan.repo;

import com.caseplan.entity.Attorney;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AttorneyRepo extends JpaRepository<Attorney, Long> {
    Optional<Attorney> findByBarNumber(String barNumber);
}
