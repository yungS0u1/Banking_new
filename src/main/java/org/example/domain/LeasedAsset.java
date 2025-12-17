package org.example.domain;

import javax.persistence.*;
import java.math.BigDecimal;
import org.example.domain.InsuranceCompany;


@Entity
public class LeasedAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String assetType;   // AUTO / EQUIPMENT
    private String name;        // Марка/модель или название
    private String serialNumber; // VIN/серийный
    private BigDecimal price;   // Стоимость

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurer_id")
    private InsuranceCompany insurer;

    public InsuranceCompany getInsurer() { return insurer; }
    public void setInsurer(InsuranceCompany insurer) { this.insurer = insurer; }

}
