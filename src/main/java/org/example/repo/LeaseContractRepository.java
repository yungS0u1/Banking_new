package org.example.repo;

import org.example.domain.LeaseContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeaseContractRepository extends JpaRepository<LeaseContract, Long> {
    Optional<LeaseContract> findByApplicationId(Long applicationId);
}
