package org.example.repo;

import org.example.domain.PaymentScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentScheduleItemRepository extends JpaRepository<PaymentScheduleItem, Long> {
    List<PaymentScheduleItem> findByApplicationIdOrderByPaymentNoAsc(Long applicationId);
}
