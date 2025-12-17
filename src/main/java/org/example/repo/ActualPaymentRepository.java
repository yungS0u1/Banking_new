package org.example.repo;

import org.example.domain.ActualPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ActualPaymentRepository extends JpaRepository<ActualPayment, Long> {
    List<ActualPayment> findByContractIdOrderByPaymentDateAsc(Long contractId);
    List<ActualPayment> findByContractIdAndPaymentDateLessThanEqualOrderByPaymentDateAsc(Long contractId, LocalDate date);
}
