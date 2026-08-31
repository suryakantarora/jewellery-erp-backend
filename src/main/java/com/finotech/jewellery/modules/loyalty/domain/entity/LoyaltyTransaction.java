package com.finotech.jewellery.modules.loyalty.domain.entity;

import com.finotech.jewellery.modules.loyalty.domain.enums.LoyaltyTransactionType;
import com.finotech.jewellery.shared.common.BaseEntity;
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
 * One movement on a loyalty account. Append-only: a correction is another
 * transaction, never an edit of an existing one.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "loyalty_transaction", schema = "crm")
public class LoyaltyTransaction extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private LoyaltyTransactionType transactionType;

    /** Signed: positive for a credit, negative for a debit. */
    @Column(name = "points", nullable = false)
    private long points;

    /** Balance immediately after this movement, for statement reconstruction. */
    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    /** Money value the points represented, where relevant. */
    @Column(name = "monetary_value", precision = 19, scale = 4)
    private BigDecimal monetaryValue;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "branch_id")
    private UUID branchId;

    /** Null means the points do not expire. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "expired", nullable = false)
    private boolean expired;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
