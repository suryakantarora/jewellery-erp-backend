package com.finotech.jewellery.modules.procurement.domain.entity;

import com.finotech.jewellery.modules.procurement.domain.enums.GoodsReceiptStatus;
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
 * A physical delivery booked against a purchase order.
 *
 * <p>Accepting a receipt is the moment stock comes into existence: one
 * serialized {@code JewelleryItem} is created per received piece, through the
 * inventory module rather than by writing item rows here.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "goods_receipt", schema = "procurement")
public class GoodsReceipt extends BaseEntity {

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber;

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GoodsReceiptStatus status = GoodsReceiptStatus.DRAFT;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "supplier_delivery_note", length = 100)
    private String supplierDeliveryNote;

    @Column(name = "quality_checked_by", length = 100)
    private String qualityCheckedBy;

    @Column(name = "quality_checked_at")
    private Instant qualityCheckedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<GoodsReceiptLine> lines = new ArrayList<>();

    public void addLine(GoodsReceiptLine line) {
        line.setGoodsReceipt(this);
        lines.add(line);
    }
}
