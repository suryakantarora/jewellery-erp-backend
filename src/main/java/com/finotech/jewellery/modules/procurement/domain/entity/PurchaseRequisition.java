package com.finotech.jewellery.modules.procurement.domain.entity;

import com.finotech.jewellery.modules.procurement.domain.enums.RequisitionStatus;
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
 * A branch's request to buy stock, raised before any supplier is committed to.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "purchase_requisition", schema = "procurement")
public class PurchaseRequisition extends BaseEntity {

    @Column(name = "reference_number", nullable = false, unique = true, length = 50)
    private String referenceNumber;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RequisitionStatus status = RequisitionStatus.DRAFT;

    @Column(name = "required_by")
    private LocalDate requiredBy;

    @Column(name = "justification", length = 500)
    private String justification;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<PurchaseRequisitionLine> lines = new ArrayList<>();

    public void addLine(PurchaseRequisitionLine line) {
        line.setRequisition(this);
        lines.add(line);
    }
}
