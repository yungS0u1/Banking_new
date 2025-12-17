package org.example.web.dto;

import java.math.BigDecimal;

public class ActualPaymentForm {
    private String paymentDate; // yyyy-MM-dd
    private BigDecimal amount;
    private String comment;

    public String getPaymentDate() { return paymentDate; }
    public void setPaymentDate(String paymentDate) { this.paymentDate = paymentDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
