package com.finotech.jewellery.modules.procurement.domain.entity;

import com.finotech.jewellery.modules.procurement.domain.enums.PurchaseOrderStatus;
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
 * A commitment to a supplier. Once approved it can be received against, in full
 * or in parts, until every line is satisfied.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "purchase_order", schema = "procurement")
public class PurchaseOrder extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    /** Where received goods will land. */
    @Column(name = "delivery_location_id", nullable = false)
    private UUID deliveryLocationId;

    @Column(name = "requisition_id")
    private UUID requisitionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    @Column(name = "estimated_total", precision = 19, scale = 4)
    private BigDecimal estimatedTotal;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public void addLine(PurchaseOrderLine line) {
        line.setPurchaseOrder(this);
        lines.add(line);
    }

    public boolean isEditable() {
        return status == PurchaseOrderStatus.DRAFT
                || status == PurchaseOrderStatus.PENDING_APPROVAL;
    }

    /** True once every line has been received in full. */
    public boolean isFullyReceived() {
        return lines.stream().allMatch(PurchaseOrderLine::isFullyReceived);
    }
}
