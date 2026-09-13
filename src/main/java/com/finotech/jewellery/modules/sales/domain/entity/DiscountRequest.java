package com.finotech.jewellery.modules.sales.domain.entity;

import com.finotech.jewellery.modules.sales.domain.enums.DiscountRequestStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A salesperson's request for a discount beyond what the branch policy lets
 * them give on their own. A manager decides it; a later sale consumes it.
 *
 * <p>Exactly one of {@code requestedPercentage} / {@code requestedAmount} is
 * set (enforced by a check constraint as well). Whoever raised it is the
 * {@code createdBy} audit column; the approver must be someone else.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "discount_request", schema = "sales")
public class DiscountRequest extends BaseEntity {

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "jewellery_item_id")
    private UUID jewelleryItemId;

    @Column(name = "quotation_id")
    private UUID quotationId;

    @Column(name = "requested_percentage", precision = 5, scale = 2)
    private BigDecimal requestedPercentage;

    @Column(name = "requested_amount", precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DiscountRequestStatus status = DiscountRequestStatus.PENDING;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_by_sale_id")
    private UUID consumedBySaleId;

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean isPercentage() {
        return requestedPercentage != null;
    }
}
