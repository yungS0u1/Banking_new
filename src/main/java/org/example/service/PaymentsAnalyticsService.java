package org.example.service;

import org.example.domain.ActualPayment;
import org.example.domain.PaymentScheduleItem;
import org.example.repo.ActualPaymentRepository;
import org.example.repo.PaymentScheduleItemRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PaymentsAnalyticsService {

    private final PaymentScheduleItemRepository planRepo;
    private final ActualPaymentRepository factRepo;

    public PaymentsAnalyticsService(PaymentScheduleItemRepository planRepo,
                                    ActualPaymentRepository factRepo) {
        this.planRepo = planRepo;
        this.factRepo = factRepo;
    }

    /** Сколько по плану должно быть оплачено к дате */
    public BigDecimal plannedPaidUpTo(Long applicationId, LocalDate date) {
        List<PaymentScheduleItem> plan =
                planRepo.findByApplicationIdOrderByPaymentNoAsc(applicationId);

        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentScheduleItem p : plan) {
            if (p.getDueDate() != null && !p.getDueDate().isAfter(date)) {
                sum = sum.add(nvl(p.getPaymentTotal()));
            }
        }
        return sum;
    }

    /** Сколько фактически оплачено к дате */
    public BigDecimal actuallyPaidUpTo(Long contractId, LocalDate date) {
        List<ActualPayment> facts =
                factRepo.findByContractIdAndPaymentDateLessThanEqualOrderByPaymentDateAsc(
                        contractId, date
                );

        BigDecimal sum = BigDecimal.ZERO;
        for (ActualPayment f : facts) {
            sum = sum.add(nvl(f.getAmount()));
        }
        return sum;
    }

    /** Количество просроченных платежей */
    public int overdueInstallmentsCount(Long applicationId, Long contractId, LocalDate date) {
        List<PaymentScheduleItem> plan =
                planRepo.findByApplicationIdOrderByPaymentNoAsc(applicationId);

        BigDecimal paid = actuallyPaidUpTo(contractId, date);
        BigDecimal cumulative = BigDecimal.ZERO;
        int overdueCount = 0;

        for (PaymentScheduleItem p : plan) {
            if (p.getDueDate() != null && !p.getDueDate().isAfter(date)) {
                cumulative = cumulative.add(nvl(p.getPaymentTotal()));
                if (paid.compareTo(cumulative) < 0) {
                    overdueCount++;
                }
            }
        }
        return overdueCount;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
