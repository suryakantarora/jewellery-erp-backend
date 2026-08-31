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

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "purchase_requisition_line", schema = "procurement")
public class PurchaseRequisitionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requisition_id", nullable = false)
    private PurchaseRequisition requisition;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Indicative total weight; the actual weight is recorded on receipt. */
    @Column(name = "estimated_weight", precision = 12, scale = 3)
    private BigDecimal estimatedWeight;

    @Column(name = "notes", length = 255)
    private String notes;
}
