package com.finotech.jewellery.modules.crm.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A rule-based group of customers, e.g. "spent over 50M in the last year" or
 * "Gold tier in Vientiane".
 *
 * <p>Criteria are stored as explicit columns rather than a free-form query
 * string: it keeps segments safe to evaluate and readable by staff, at the cost
 * of only supporting the dimensions that actually get used.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customer_segment", schema = "crm")
public class CustomerSegment extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    // ---------- criteria; null means "no constraint on this dimension" ----------

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "tier_code", length = 30)
    private String tierCode;

    @Column(name = "min_lifetime_spend", precision = 19, scale = 4)
    private BigDecimal minLifetimeSpend;

    @Column(name = "min_purchase_count")
    private Integer minPurchaseCount;

    /** Customers who have not purchased in this many days. */
    @Column(name = "inactive_days")
    private Integer inactiveDays;

    /** Customers with a birthday in this month, for anniversary outreach. */
    @Column(name = "birthday_month")
    private Integer birthdayMonth;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
