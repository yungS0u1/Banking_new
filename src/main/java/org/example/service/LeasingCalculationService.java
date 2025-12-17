package org.example.service;

import org.example.domain.LeaseApplication;
import org.example.domain.PaymentScheduleItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class LeasingCalculationService {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    public List<PaymentScheduleItem> buildAnnuitySchedule(LeaseApplication app) {
        BigDecimal principal = nvl(app.getFinancedAmount());
        int n = app.getTermMonths() == null ? 0 : app.getTermMonths();
        BigDecimal annual = nvl(app.getAnnualRatePercent());
        LocalDate start = app.getStartDate() == null ? LocalDate.now() : app.getStartDate();

        if (n <= 0) throw new IllegalArgumentException("termMonths must be > 0");
        if (principal.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("financedAmount must be > 0");

        // r = annual / 12 / 100
        BigDecimal r = annual.divide(new BigDecimal("12"), MC).divide(new BigDecimal("100"), MC);

        // payment = P * r * (1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);
        BigDecimal pow = onePlusR.pow(n, MC);

        BigDecimal payment;
        if (r.compareTo(BigDecimal.ZERO) == 0) {
            payment = principal.divide(new BigDecimal(n), MC);
        } else {
            BigDecimal numerator = principal.multiply(r, MC).multiply(pow, MC);
            BigDecimal denominator = pow.subtract(BigDecimal.ONE, MC);
            payment = numerator.divide(denominator, MC);
        }

        payment = money(payment);

        List<PaymentScheduleItem> items = new ArrayList<PaymentScheduleItem>();
        BigDecimal balance = principal;

        for (int i = 1; i <= n; i++) {
            BigDecimal interest = money(balance.multiply(r, MC));
            BigDecimal principalPart = money(payment.subtract(interest, MC));

            // на последнем платеже закрываем хвост из-за округлений
            if (i == n) {
                principalPart = money(balance);
                payment = money(principalPart.add(interest, MC));
            }

            balance = money(balance.subtract(principalPart, MC));
            if (balance.compareTo(BigDecimal.ZERO) < 0) balance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            PaymentScheduleItem row = new PaymentScheduleItem();
            row.setPaymentNo(i);
            row.setDueDate(start.plusMonths(i));
            row.setPaymentTotal(payment);
            row.setPaymentInterest(interest);
            row.setPaymentPrincipal(principalPart);
            row.setBalanceAfter(balance);

            items.add(row);
        }

        return items;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
