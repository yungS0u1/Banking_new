package org.example.domain;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class LeaseApplication {

    public enum Status {
        NEW, APPROVED, REJECTED, CONTRACTED
    }

    @Column(length = 500)
    private String rejectionReason;

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String applicationNumber;

    private LocalDate createdDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private LeasedAsset asset;

    private BigDecimal assetPrice;
    private BigDecimal advanceAmount;     // аванс в деньгах
    private BigDecimal financedAmount;    // сумма финансирования = price - advance

    private Integer termMonths;
    private BigDecimal annualRatePercent; // годовая ставка %

    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    private Status status;

    public Long getId() { return id; }

    public String getApplicationNumber() { return applicationNumber; }
    public void setApplicationNumber(String applicationNumber) { this.applicationNumber = applicationNumber; }

    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public LeasedAsset getAsset() { return asset; }
    public void setAsset(LeasedAsset asset) { this.asset = asset; }

    public BigDecimal getAssetPrice() { return assetPrice; }
    public void setAssetPrice(BigDecimal assetPrice) { this.assetPrice = assetPrice; }

    public BigDecimal getAdvanceAmount() { return advanceAmount; }
    public void setAdvanceAmount(BigDecimal advanceAmount) { this.advanceAmount = advanceAmount; }

    public BigDecimal getFinancedAmount() { return financedAmount; }
    public void setFinancedAmount(BigDecimal financedAmount) { this.financedAmount = financedAmount; }

    public Integer getTermMonths() { return termMonths; }
    public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }

    public BigDecimal getAnnualRatePercent() { return annualRatePercent; }
    public void setAnnualRatePercent(BigDecimal annualRatePercent) { this.annualRatePercent = annualRatePercent; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }


}
