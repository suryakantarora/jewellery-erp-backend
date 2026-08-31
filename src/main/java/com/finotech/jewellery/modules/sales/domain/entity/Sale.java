package com.finotech.jewellery.modules.sales.domain.entity;

import com.finotech.jewellery.modules.sales.domain.enums.SaleStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A retail sale.
 *
 * <p>Every monetary component is captured on the sale and its lines at the
 * moment of confirmation. Later changes to metal rates, tax or making charges
 * never alter a historical sale (section 17).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "sale", schema = "sales")
public class Sale extends BaseEntity {

    @Column(name = "sale_number", nullable = false, unique = true, length = 50)
    private String saleNumber;

    @Column(name = "invoice_number", unique = true, length = 50)
    private String invoiceNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "quotation_id")
    private UUID quotationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SaleStatus status = SaleStatus.DRAFT;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    @Column(name = "sub_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal subTotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    /** Value allowed for old jewellery taken in part-exchange. */
    @Column(name = "exchange_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal exchangeCredit = BigDecimal.ZERO;

    /** Loyalty points spent on this sale, and what they were worth. */
    @Column(name = "loyalty_points_redeemed", nullable = false)
    private long loyaltyPointsRedeemed;

    @Column(name = "loyalty_redemption_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal loyaltyRedemptionValue = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "salesperson_id")
    private UUID salespersonId;

    @Column(name = "discount_approved_by", length = 100)
    private String discountApprovedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<SaleLine> lines = new ArrayList<>();

    public void addLine(SaleLine line) {
        line.setSale(this);
        lines.add(line);
    }

    /**
     * What the customer still owes, after exchange credit, loyalty redemption
     * and payments already taken.
     */
    public BigDecimal outstandingAmount() {
        return totalAmount
                .subtract(exchangeCredit)
                .subtract(loyaltyRedemptionValue)
                .subtract(paidAmount);
    }

    /** The cash amount the customer is expected to settle. */
    public BigDecimal amountPayable() {
        return totalAmount.subtract(exchangeCredit).subtract(loyaltyRedemptionValue);
    }

    public boolean isFullyPaid() {
        return outstandingAmount().signum() <= 0;
    }

    /** True when nothing was taken off the price other than payment. */
    public boolean hasLoyaltyRedemption() {
        return loyaltyPointsRedeemed > 0;
    }
}
