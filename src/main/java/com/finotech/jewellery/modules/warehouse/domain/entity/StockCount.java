package com.finotech.jewellery.modules.warehouse.domain.entity;

import com.finotech.jewellery.modules.warehouse.domain.enums.StockCountStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A physical stock verification of one location.
 *
 * <p>Opening a count snapshots what the system believes is there; staff then
 * scan what is actually present. The difference is the variance, and a count
 * with variances cannot be closed without a second approver — the same
 * dual-control principle the vault uses for movements.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "stock_count", schema = "inventory")
public class StockCount extends BaseEntity {

    @Column(name = "reference_number", nullable = false, unique = true, length = 50)
    private String referenceNumber;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StockCountStatus status = StockCountStatus.IN_PROGRESS;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    /** True when the location requires two approvers, e.g. a vault. */
    @Column(name = "dual_authorization", nullable = false)
    private boolean dualAuthorization;

    @Column(name = "expected_count", nullable = false)
    private int expectedCount;

    @Column(name = "counted_count", nullable = false)
    private int countedCount;

    @Column(name = "missing_count", nullable = false)
    private int missingCount;

    @Column(name = "unexpected_count", nullable = false)
    private int unexpectedCount;

    @Column(name = "counted_by", length = 100)
    private String countedBy;

    @Column(name = "counted_at")
    private Instant countedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "second_approved_by", length = 100)
    private String secondApprovedBy;

    @Column(name = "second_approved_at")
    private Instant secondApprovedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<StockCountLine> lines = new ArrayList<>();

    public void addLine(StockCountLine line) {
        line.setStockCount(this);
        lines.add(line);
    }

    public boolean hasVariance() {
        return missingCount > 0 || unexpectedCount > 0;
    }
}
