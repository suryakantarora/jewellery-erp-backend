package com.finotech.jewellery.modules.procurement.api.response;

import com.finotech.jewellery.modules.procurement.domain.entity.GoodsReceipt;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseOrder;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseRequisition;
import com.finotech.jewellery.modules.procurement.domain.entity.SupplierInvoice;
import com.finotech.jewellery.modules.procurement.domain.enums.GoodsReceiptStatus;
import com.finotech.jewellery.modules.procurement.domain.enums.PurchaseOrderStatus;
import com.finotech.jewellery.modules.procurement.domain.enums.RequisitionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response shapes for the procurement documents. */
public final class ProcurementResponses {

    private ProcurementResponses() {
    }

    public record RequisitionResponse(UUID id, String referenceNumber, UUID branchId,
                                      RequisitionStatus status, LocalDate requiredBy,
                                      String justification, String approvedBy, Instant approvedAt,
                                      String rejectionReason, List<RequisitionLine> lines,
                                      Instant createdAt, String createdBy) {

        public record RequisitionLine(UUID id, UUID productId, int quantity,
                                      BigDecimal estimatedWeight, String notes) {
        }

        public static RequisitionResponse from(PurchaseRequisition r) {
            return new RequisitionResponse(r.getId(), r.getReferenceNumber(), r.getBranchId(),
                    r.getStatus(), r.getRequiredBy(), r.getJustification(), r.getApprovedBy(),
                    r.getApprovedAt(), r.getRejectionReason(),
                    r.getLines().stream().map(l -> new RequisitionLine(l.getId(), l.getProductId(),
                            l.getQuantity(), l.getEstimatedWeight(), l.getNotes())).toList(),
                    r.getCreatedAt(), r.getCreatedBy());
        }
    }

    public record PurchaseOrderResponse(UUID id, String orderNumber, UUID supplierId, UUID branchId,
                                        UUID deliveryLocationId, UUID requisitionId,
                                        PurchaseOrderStatus status, LocalDate orderDate,
                                        LocalDate expectedDeliveryDate, String currency,
                                        BigDecimal estimatedTotal, String approvedBy,
                                        Instant approvedAt, String rejectionReason, String notes,
                                        List<PurchaseOrderLineResponse> lines, Instant createdAt,
                                        String createdBy) {

        public record PurchaseOrderLineResponse(UUID id, UUID productId, UUID metalId, UUID purityId,
                                                int orderedQuantity, int receivedQuantity,
                                                int outstandingQuantity, BigDecimal estimatedWeight,
                                                BigDecimal ratePerGram,
                                                BigDecimal makingChargePerUnit,
                                                BigDecimal lineTotal, String notes) {
        }

        public static PurchaseOrderResponse from(PurchaseOrder o) {
            return new PurchaseOrderResponse(o.getId(), o.getOrderNumber(), o.getSupplierId(),
                    o.getBranchId(), o.getDeliveryLocationId(), o.getRequisitionId(), o.getStatus(),
                    o.getOrderDate(), o.getExpectedDeliveryDate(), o.getCurrency(),
                    o.getEstimatedTotal(), o.getApprovedBy(), o.getApprovedAt(),
                    o.getRejectionReason(), o.getNotes(),
                    o.getLines().stream().map(l -> new PurchaseOrderLineResponse(l.getId(),
                            l.getProductId(), l.getMetalId(), l.getPurityId(), l.getOrderedQuantity(),
                            l.getReceivedQuantity(), l.outstandingQuantity(), l.getEstimatedWeight(),
                            l.getRatePerGram(), l.getMakingChargePerUnit(), l.getLineTotal(),
                            l.getNotes())).toList(),
                    o.getCreatedAt(), o.getCreatedBy());
        }
    }

    public record GoodsReceiptResponse(UUID id, String receiptNumber, UUID purchaseOrderId,
                                       UUID supplierId, UUID locationId, UUID branchId,
                                       GoodsReceiptStatus status, LocalDate receiptDate,
                                       String supplierDeliveryNote, String qualityCheckedBy,
                                       Instant qualityCheckedAt, String rejectionReason,
                                       String notes, List<GoodsReceiptLineResponse> lines,
                                       Instant createdAt, String createdBy) {

        public record GoodsReceiptLineResponse(UUID id, UUID purchaseOrderLineId, UUID productId,
                                               BigDecimal grossWeight, BigDecimal stoneWeight,
                                               BigDecimal purchaseCost, BigDecimal makingCost,
                                               BigDecimal stoneCost, String barcode, String rfidTag,
                                               UUID jewelleryItemId, String notes) {
        }

        public static GoodsReceiptResponse from(GoodsReceipt g) {
            return new GoodsReceiptResponse(g.getId(), g.getReceiptNumber(), g.getPurchaseOrderId(),
                    g.getSupplierId(), g.getLocationId(), g.getBranchId(), g.getStatus(),
                    g.getReceiptDate(), g.getSupplierDeliveryNote(), g.getQualityCheckedBy(),
                    g.getQualityCheckedAt(), g.getRejectionReason(), g.getNotes(),
                    g.getLines().stream().map(l -> new GoodsReceiptLineResponse(l.getId(),
                            l.getPurchaseOrderLineId(), l.getProductId(), l.getGrossWeight(),
                            l.getStoneWeight(), l.getPurchaseCost(), l.getMakingCost(),
                            l.getStoneCost(), l.getBarcode(), l.getRfidTag(), l.getJewelleryItemId(),
                            l.getNotes())).toList(),
                    g.getCreatedAt(), g.getCreatedBy());
        }
    }

    public record SupplierInvoiceResponse(UUID id, String invoiceNumber, UUID supplierId,
                                          UUID purchaseOrderId, UUID goodsReceiptId,
                                          LocalDate invoiceDate, LocalDate dueDate, String currency,
                                          BigDecimal subTotal, BigDecimal taxAmount,
                                          BigDecimal totalAmount, BigDecimal paidAmount,
                                          BigDecimal outstandingAmount, String status,
                                          String notes) {

        public static SupplierInvoiceResponse from(SupplierInvoice i) {
            return new SupplierInvoiceResponse(i.getId(), i.getInvoiceNumber(), i.getSupplierId(),
                    i.getPurchaseOrderId(), i.getGoodsReceiptId(), i.getInvoiceDate(),
                    i.getDueDate(), i.getCurrency(), i.getSubTotal(), i.getTaxAmount(),
                    i.getTotalAmount(), i.getPaidAmount(), i.outstandingAmount(), i.getStatus(),
                    i.getNotes());
        }
    }
}
