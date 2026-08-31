package com.finotech.jewellery.modules.procurement.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The supplier's bill for a delivery. Finance settles against this; it is
 * deliberately separate from the goods receipt because invoices and deliveries
 * do not always align one to one.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "supplier_invoice", schema = "procurement")
public class SupplierInvoice extends BaseEntity {

    @Column(name = "invoice_number", nullable = false, length = 100)
    private String invoiceNumber;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "goods_receipt_id")
    private UUID goodsReceiptId;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    @Column(name = "sub_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal subTotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "UNPAID";

    @Column(name = "notes", length = 500)
    private String notes;

    public BigDecimal outstandingAmount() {
        return totalAmount.subtract(paidAmount);
    }
}
