package com.finotech.jewellery.modules.exchange.domain.entity;

import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeStatus;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeType;
import com.finotech.jewellery.shared.common.BaseEntity;
import com.finotech.jewellery.shared.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Old jewellery taken in from a customer for exchange or buyback.
 *
 * <p>Every measurement that justifies the money paid out is kept: the gross and
 * net weights, the tested purity, the rate applied and any deductions. A
 * valuation can therefore be defended long after the fact, which matters
 * because buyback is the easiest place in a jewellery business to lose money
 * quietly.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "exchange_intake", schema = "sales")
public class ExchangeIntake extends BaseEntity {

    @Column(name = "reference_number", nullable = false, unique = true, length = 50)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange_type", nullable = false, length = 20)
    private ExchangeType exchangeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExchangeStatus status = ExchangeStatus.RECEIVED;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "item_count", nullable = false)
    private int itemCount = 1;

    /** Set when the piece was originally sold by this business. */
    @Column(name = "original_item_id")
    private UUID originalItemId;

    // ---------- weight check ----------

    @Column(name = "metal_id", nullable = false)
    private UUID metalId;

    @Column(name = "gross_weight", precision = 12, scale = 3)
    private BigDecimal grossWeight;

    /** Weight of stones and non-metal parts, deducted before valuing. */
    @Column(name = "stone_weight", precision = 12, scale = 3)
    private BigDecimal stoneWeight;

    @Column(name = "net_weight", precision = 12, scale = 3)
    private BigDecimal netWeight;

    @Column(name = "weighed_by", length = 100)
    private String weighedBy;

    @Column(name = "weighed_at")
    private Instant weighedAt;

    // ---------- purity check ----------

    @Column(name = "declared_purity_id")
    private UUID declaredPurityId;

    /** Purity established by testing, which is what the valuation uses. */
    @Column(name = "tested_purity_id")
    private UUID testedPurityId;

    @Column(name = "tested_fineness", precision = 9, scale = 6)
    private BigDecimal testedFineness;

    @Column(name = "test_method", length = 50)
    private String testMethod;

    @Column(name = "tested_by", length = 100)
    private String testedBy;

    @Column(name = "tested_at")
    private Instant testedAt;

    // ---------- valuation ----------

    @Column(name = "rate_id")
    private UUID rateId;

    @Column(name = "rate_per_unit", precision = 19, scale = 4)
    private BigDecimal ratePerUnit;

    /** Pure metal content: net weight × tested fineness. */
    @Column(name = "pure_weight", precision = 12, scale = 3)
    private BigDecimal pureWeight;

    @Column(name = "gross_valuation", precision = 19, scale = 4)
    private BigDecimal grossValuation;

    /** Refining loss, testing fee or wear deduction. */
    @Column(name = "deduction_percentage", precision = 7, scale = 4)
    private BigDecimal deductionPercentage;

    @Column(name = "deduction_amount", precision = 19, scale = 4)
    private BigDecimal deductionAmount;

    @Column(name = "net_valuation", precision = 19, scale = 4)
    private BigDecimal netValuation;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    @Column(name = "valued_by", length = 100)
    private String valuedBy;

    @Column(name = "valued_at")
    private Instant valuedAt;

    // ---------- approval and settlement ----------

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Sale the credit was applied to, for an exchange. */
    @Column(name = "applied_sale_id")
    private UUID appliedSaleId;

    /** Scrap batch the metal went into, once completed. */
    @Column(name = "scrap_batch_id")
    private UUID scrapBatchId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * @throws ConflictException if the workflow step is out of order
     */
    public void transitionTo(ExchangeStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Intake " + referenceNumber + " cannot move from "
                    + status + " to " + target);
        }
        this.status = target;
    }
}
