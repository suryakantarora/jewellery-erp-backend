package com.finotech.jewellery.modules.procurement.application.service;

import com.finotech.jewellery.modules.inventory.application.InventoryOperations;
import com.finotech.jewellery.modules.procurement.api.request.GoodsReceiptRequest;
import com.finotech.jewellery.modules.procurement.api.response.ProcurementResponses.GoodsReceiptResponse;
import com.finotech.jewellery.modules.procurement.domain.entity.GoodsReceipt;
import com.finotech.jewellery.modules.procurement.domain.entity.GoodsReceiptLine;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseOrder;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseOrderLine;
import com.finotech.jewellery.modules.procurement.domain.enums.GoodsReceiptStatus;
import com.finotech.jewellery.modules.procurement.domain.enums.PurchaseOrderStatus;
import com.finotech.jewellery.modules.procurement.infrastructure.repository.GoodsReceiptRepository;
import com.finotech.jewellery.modules.procurement.infrastructure.repository.PurchaseOrderRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Goods receipt and quality check.
 *
 * <p>A receipt is recorded first and accepted second. Only on acceptance are
 * serialized items created — through {@link InventoryOperations}, never by
 * writing inventory tables — so goods that fail quality check never become
 * stock. The whole acceptance runs in one transaction: either every piece
 * exists and the order advances, or nothing does.
 */
@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    private final GoodsReceiptRepository receiptRepository;
    private final PurchaseOrderRepository orderRepository;
    private final InventoryOperations inventory;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<GoodsReceiptResponse> search(GoodsReceiptStatus status, UUID purchaseOrderId,
                                                     UUID branchId, Pageable pageable) {
        return PageResponse.of(receiptRepository.search(status, purchaseOrderId, branchId, pageable),
                GoodsReceiptResponse::from);
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponse get(UUID id) {
        return GoodsReceiptResponse.from(requireReceipt(id));
    }

    @Transactional
    public GoodsReceiptResponse create(GoodsReceiptRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            var existing = receiptRepository.findByExternalReference(idempotencyKey);
            if (existing.isPresent()) {
                return GoodsReceiptResponse.from(existing.get());
            }
        }

        PurchaseOrder order = orderRepository.findWithLinesById(request.purchaseOrderId())
                .orElseThrow(() -> NotFoundException.of("PurchaseOrder", request.purchaseOrderId()));
        if (!order.getStatus().acceptsReceipt()) {
            throw new ConflictException("Purchase order " + order.getOrderNumber() + " is "
                    + order.getStatus() + " and cannot receive goods");
        }
        SecurityUtils.requireBranchAccess(order.getBranchId());

        Map<UUID, PurchaseOrderLine> orderLines = new HashMap<>();
        order.getLines().forEach(l -> orderLines.put(l.getId(), l));

        // Count what this receipt claims per order line, so an over-delivery is
        // caught before anything is written.
        Map<UUID, Integer> claimed = new HashMap<>();
        for (GoodsReceiptRequest.Line line : request.lines()) {
            claimed.merge(line.purchaseOrderLineId(), 1, Integer::sum);
        }
        for (Map.Entry<UUID, Integer> entry : claimed.entrySet()) {
            PurchaseOrderLine orderLine = orderLines.get(entry.getKey());
            if (orderLine == null) {
                throw new ValidationException(
                        "Line " + entry.getKey() + " does not belong to this purchase order");
            }
            if (entry.getValue() > orderLine.outstandingQuantity()) {
                throw new ValidationException("Receiving " + entry.getValue() + " of product "
                        + orderLine.getProductId() + " exceeds the outstanding quantity of "
                        + orderLine.outstandingQuantity());
            }
        }

        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setReceiptNumber(CodeGenerator.reference("GRN"));
        receipt.setPurchaseOrderId(order.getId());
        receipt.setSupplierId(order.getSupplierId());
        receipt.setLocationId(order.getDeliveryLocationId());
        receipt.setBranchId(order.getBranchId());
        receipt.setReceiptDate(request.receiptDate() == null ? LocalDate.now() : request.receiptDate());
        receipt.setSupplierDeliveryNote(request.supplierDeliveryNote());
        receipt.setNotes(request.notes());
        receipt.setStatus(GoodsReceiptStatus.PENDING_QUALITY_CHECK);
        receipt.setExternalReference(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);

        for (GoodsReceiptRequest.Line line : request.lines()) {
            PurchaseOrderLine orderLine = orderLines.get(line.purchaseOrderLineId());
            GoodsReceiptLine entity = new GoodsReceiptLine();
            entity.setPurchaseOrderLineId(orderLine.getId());
            entity.setProductId(orderLine.getProductId());
            entity.setMetalId(orderLine.getMetalId());
            entity.setPurityId(orderLine.getPurityId());
            entity.setGrossWeight(line.grossWeight());
            entity.setStoneWeight(line.stoneWeight());
            entity.setPurchaseCost(line.purchaseCost());
            entity.setMakingCost(line.makingCost());
            entity.setStoneCost(line.stoneCost());
            entity.setHallmarkNumber(line.hallmarkNumber());
            entity.setBarcode(line.barcode());
            entity.setRfidTag(line.rfidTag());
            entity.setNotes(line.notes());
            receipt.addLine(entity);
        }

        GoodsReceipt saved = receiptRepository.save(receipt);
        auditService.record("GOODS_RECEIPT_CREATED", "GoodsReceipt", saved.getId(), null,
                Map.of("purchaseOrder", order.getOrderNumber(), "pieces", saved.getLines().size()),
                saved.getBranchId());
        return GoodsReceiptResponse.from(saved);
    }

    /**
     * Passes quality check and brings the delivery into stock. Each line becomes
     * one serialized item in DRAFT, ready to be tagged and released.
     */
    @Transactional
    public GoodsReceiptResponse accept(UUID id) {
        GoodsReceipt receipt = requireReceipt(id);
        if (receipt.getStatus() != GoodsReceiptStatus.PENDING_QUALITY_CHECK) {
            throw new ConflictException("Receipt " + receipt.getReceiptNumber() + " is "
                    + receipt.getStatus() + " and cannot be accepted");
        }

        PurchaseOrder order = orderRepository.findWithLinesById(receipt.getPurchaseOrderId())
                .orElseThrow(() -> NotFoundException.of("PurchaseOrder", receipt.getPurchaseOrderId()));
        Map<UUID, PurchaseOrderLine> orderLines = new HashMap<>();
        order.getLines().forEach(l -> orderLines.put(l.getId(), l));

        for (GoodsReceiptLine line : receipt.getLines()) {
            UUID itemId = inventory.intake(new InventoryOperations.ItemIntake(
                    line.getProductId(),
                    line.getMetalId(),
                    line.getPurityId(),
                    line.getGrossWeight(),
                    line.getStoneWeight(),
                    line.getPurchaseCost(),
                    line.getMakingCost(),
                    line.getStoneCost(),
                    receipt.getSupplierId(),
                    receipt.getLocationId(),
                    line.getBarcode(),
                    line.getRfidTag(),
                    line.getHallmarkNumber(),
                    "GoodsReceipt",
                    receipt.getReceiptNumber(),
                    line.getNotes()));
            line.setJewelleryItemId(itemId);

            PurchaseOrderLine orderLine = orderLines.get(line.getPurchaseOrderLineId());
            orderLine.setReceivedQuantity(orderLine.getReceivedQuantity() + 1);
        }

        receipt.setStatus(GoodsReceiptStatus.ACCEPTED);
        receipt.setQualityCheckedBy(SecurityUtils.currentUsername().orElse("system"));
        receipt.setQualityCheckedAt(Instant.now());

        order.setStatus(order.isFullyReceived()
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED);

        auditService.record("GOODS_RECEIPT_ACCEPTED", "GoodsReceipt", id, null,
                Map.of("itemsCreated", receipt.getLines().size(),
                        "purchaseOrderStatus", order.getStatus()),
                receipt.getBranchId());

        // Finance brings the stock onto the balance sheet and recognises what is
        // owed for it.
        java.math.BigDecimal totalCost = receipt.getLines().stream()
                .map(l -> com.finotech.jewellery.shared.utils.MoneyUtils.nullSafe(l.getPurchaseCost())
                        .add(com.finotech.jewellery.shared.utils.MoneyUtils.nullSafe(l.getMakingCost()))
                        .add(com.finotech.jewellery.shared.utils.MoneyUtils.nullSafe(l.getStoneCost())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        events.publish(new DomainEvents.GoodsReceived(receipt.getId(), receipt.getSupplierId(),
                receipt.getBranchId(), receipt.getReceiptNumber(), totalCost,
                receipt.getLines().size()));

        return GoodsReceiptResponse.from(receipt);
    }

    /**
     * Fails quality check. No stock is created, and the order lines stay
     * outstanding so the supplier can redeliver.
     */
    @Transactional
    public GoodsReceiptResponse reject(UUID id, String reason) {
        GoodsReceipt receipt = requireReceipt(id);
        if (receipt.getStatus() != GoodsReceiptStatus.PENDING_QUALITY_CHECK) {
            throw new ConflictException("Only a receipt awaiting quality check can be rejected");
        }
        receipt.setStatus(GoodsReceiptStatus.REJECTED);
        receipt.setRejectionReason(reason);
        receipt.setQualityCheckedBy(SecurityUtils.currentUsername().orElse("system"));
        receipt.setQualityCheckedAt(Instant.now());

        auditService.record("GOODS_RECEIPT_REJECTED", "GoodsReceipt", id, null,
                Map.of("reason", String.valueOf(reason)), receipt.getBranchId());
        return GoodsReceiptResponse.from(receipt);
    }

    private GoodsReceipt requireReceipt(UUID id) {
        return receiptRepository.findWithLinesById(id)
                .orElseThrow(() -> NotFoundException.of("GoodsReceipt", id));
    }
}
