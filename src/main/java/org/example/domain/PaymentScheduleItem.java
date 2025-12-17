package org.example.domain;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class PaymentScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private LeaseApplication application;

    private Integer paymentNo;
    private LocalDate dueDate;

    private BigDecimal paymentTotal;
    private BigDecimal paymentInterest;
    private BigDecimal paymentPrincipal;

    private BigDecimal balanceAfter;

    public Long getId() { return id; }

    public LeaseApplication getApplication() { return application; }
    public void setApplication(LeaseApplication application) { this.application = application; }

    public Integer getPaymentNo() { return paymentNo; }
    public void setPaymentNo(Integer paymentNo) { this.paymentNo = paymentNo; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getPaymentTotal() { return paymentTotal; }
    public void setPaymentTotal(BigDecimal paymentTotal) { this.paymentTotal = paymentTotal; }

    public BigDecimal getPaymentInterest() { return paymentInterest; }
    public void setPaymentInterest(BigDecimal paymentInterest) { this.paymentInterest = paymentInterest; }

    public BigDecimal getPaymentPrincipal() { return paymentPrincipal; }
    public void setPaymentPrincipal(BigDecimal paymentPrincipal) { this.paymentPrincipal = paymentPrincipal; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
}
