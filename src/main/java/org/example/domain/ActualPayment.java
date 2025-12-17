package org.example.domain;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class ActualPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private LeaseContract contract;

    private LocalDate paymentDate;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 200)
    private String comment;

    public Long getId() { return id; }

    public LeaseContract getContract() { return contract; }
    public void setContract(LeaseContract contract) { this.contract = contract; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
