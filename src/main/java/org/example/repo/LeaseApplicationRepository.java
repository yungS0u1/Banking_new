package org.example.repo;

import org.example.domain.LeaseApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseApplicationRepository extends JpaRepository<LeaseApplication, Long> {}
