package com.finotech.jewellery.modules.sales.domain.entity;

import com.finotech.jewellery.modules.sales.domain.enums.QuotationStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A price offer held for a customer.
 *
 * <p>Metal rates move daily, so a quotation records the prices it was built
 * from and carries an expiry: after that date it must be re-priced rather than
 * honoured (section 17).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "quotation", schema = "sales")
public class Quotation extends BaseEntity {

    @Column(name = "quotation_number", nullable = false, unique = true, length = 50)
    private String quotationNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private QuotationStatus status = QuotationStatus.DRAFT;

    @Column(name = "quotation_date", nullable = false)
    private LocalDate quotationDate;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    @Column(name = "sub_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal subTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "converted_sale_id")
    private UUID convertedSaleId;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<QuotationLine> lines = new ArrayList<>();

    public void addLine(QuotationLine line) {
        line.setQuotation(this);
        lines.add(line);
    }

    public boolean isExpired() {
        return validUntil.isBefore(LocalDate.now());
    }
}
