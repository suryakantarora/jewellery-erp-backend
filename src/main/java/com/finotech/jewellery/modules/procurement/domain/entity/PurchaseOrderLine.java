package com.finotech.jewellery.modules.procurement.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One ordered product line. {@code receivedQuantity} advances as goods receipts
 * are accepted, which is what drives partial-receipt handling.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "purchase_order_line", schema = "procurement")
public class PurchaseOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "metal_id")
    private UUID metalId;

    @Column(name = "purity_id")
    private UUID purityId;

    @Column(name = "ordered_quantity", nullable = false)
    private int orderedQuantity;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    @Column(name = "estimated_weight", precision = 12, scale = 3)
    private BigDecimal estimatedWeight;

    /** Agreed rate per unit weight, where the supplier prices by weight. */
    @Column(name = "rate_per_gram", precision = 19, scale = 4)
    private BigDecimal ratePerGram;

    @Column(name = "making_charge_per_unit", precision = 19, scale = 4)
    private BigDecimal makingChargePerUnit;

    @Column(name = "line_total", precision = 19, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "notes", length = 255)
    private String notes;

    public boolean isFullyReceived() {
        return receivedQuantity >= orderedQuantity;
    }

    public int outstandingQuantity() {
        return Math.max(orderedQuantity - receivedQuantity, 0);
    }
}
