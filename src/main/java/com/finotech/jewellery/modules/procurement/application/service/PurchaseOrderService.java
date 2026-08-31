package com.finotech.jewellery.modules.procurement.application.service;

import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;
import com.finotech.jewellery.modules.procurement.api.request.PurchaseOrderRequest;
import com.finotech.jewellery.modules.procurement.api.request.RequisitionRequest;
import com.finotech.jewellery.modules.procurement.api.response.ProcurementResponses.PurchaseOrderResponse;
import com.finotech.jewellery.modules.procurement.api.response.ProcurementResponses.RequisitionResponse;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseOrder;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseOrderLine;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseRequisition;
import com.finotech.jewellery.modules.procurement.domain.entity.PurchaseRequisitionLine;
import com.finotech.jewellery.modules.procurement.domain.enums.PurchaseOrderStatus;
import com.finotech.jewellery.modules.procurement.domain.enums.RequisitionStatus;
import com.finotech.jewellery.modules.procurement.infrastructure.repository.PurchaseOrderRepository;
import com.finotech.jewellery.modules.procurement.infrastructure.repository.PurchaseRequisitionRepository;
import com.finotech.jewellery.modules.product.application.ProductCatalog;
import com.finotech.jewellery.modules.supplier.application.SupplierDirectory;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Requisition and purchase order workflow:
 * requisition → approval → purchase order → approval → open for receipt.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseRequisitionRepository requisitionRepository;
    private final SupplierDirectory supplierDirectory;
    private final OrganizationDirectory organizationDirectory;
    private final ProductCatalog productCatalog;
    private final AuditService auditService;

    // ---------- requisition ----------

    @Transactional(readOnly = true)
    public PageResponse<RequisitionResponse> searchRequisitions(RequisitionStatus status,
                                                                UUID branchId, Pageable pageable) {
        return PageResponse.of(requisitionRepository.search(status, branchId, pageable),
                RequisitionResponse::from);
    }

    @Transactional(readOnly = true)
    public RequisitionResponse getRequisition(UUID id) {
        return RequisitionResponse.from(requireRequisition(id));
    }

    @Transactional
    public RequisitionResponse createRequisition(RequisitionRequest request) {
        if (!organizationDirectory.branchExists(request.branchId())) {
            throw NotFoundException.of("Branch", request.branchId());
        }
        SecurityUtils.requireBranchAccess(request.branchId());

        PurchaseRequisition requisition = new PurchaseRequisition();
        requisition.setReferenceNumber(CodeGenerator.reference("PR"));
        requisition.setBranchId(request.branchId());
        requisition.setRequiredBy(request.requiredBy());
        requisition.setJustification(request.justification());
        requisition.setStatus(RequisitionStatus.PENDING_APPROVAL);

        for (RequisitionRequest.Line line : request.lines()) {
            productCatalog.requireProduct(line.productId());
            PurchaseRequisitionLine entity = new PurchaseRequisitionLine();
            entity.setProductId(line.productId());
            entity.setQuantity(line.quantity());
            entity.setEstimatedWeight(line.estimatedWeight());
            entity.setNotes(line.notes());
            requisition.addLine(entity);
        }

        PurchaseRequisition saved = requisitionRepository.save(requisition);
        auditService.record("REQUISITION_CREATED", "PurchaseRequisition", saved.getId(), null,
                RequisitionResponse.from(saved), saved.getBranchId());
        return RequisitionResponse.from(saved);
    }

    @Transactional
    public RequisitionResponse approveRequisition(UUID id) {
        PurchaseRequisition requisition = requireRequisition(id);
        if (requisition.getStatus() != RequisitionStatus.PENDING_APPROVAL) {
            throw new ConflictException("Requisition " + requisition.getReferenceNumber()
                    + " is " + requisition.getStatus() + " and cannot be approved");
        }
        requisition.setStatus(RequisitionStatus.APPROVED);
        requisition.setApprovedBy(SecurityUtils.currentUsername().orElse("system"));
        requisition.setApprovedAt(Instant.now());

        auditService.record("REQUISITION_APPROVED", "PurchaseRequisition", id, null, null,
                requisition.getBranchId());
        return RequisitionResponse.from(requisition);
    }

    @Transactional
    public RequisitionResponse rejectRequisition(UUID id, String reason) {
        PurchaseRequisition requisition = requireRequisition(id);
        if (requisition.getStatus() != RequisitionStatus.PENDING_APPROVAL) {
            throw new ConflictException("Only a pending requisition can be rejected");
        }
        requisition.setStatus(RequisitionStatus.REJECTED);
        requisition.setRejectionReason(reason);
        auditService.record("REQUISITION_REJECTED", "PurchaseRequisition", id, null,
                Map.of("reason", String.valueOf(reason)), requisition.getBranchId());
        return RequisitionResponse.from(requisition);
    }

    // ---------- purchase order ----------

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> searchOrders(PurchaseOrderStatus status,
                                                            UUID supplierId, UUID branchId,
                                                            Pageable pageable) {
        return PageResponse.of(orderRepository.search(status, supplierId, branchId, pageable),
                PurchaseOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getOrder(UUID id) {
        return PurchaseOrderResponse.from(requireOrder(id));
    }

    /**
     * @param idempotencyKey optional; replaying the same key returns the order
     *                       already created rather than duplicating a commitment
     */
    @Transactional
    public PurchaseOrderResponse createOrder(PurchaseOrderRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            var existing = orderRepository.findByExternalReference(idempotencyKey);
            if (existing.isPresent()) {
                return PurchaseOrderResponse.from(existing.get());
            }
        }

        SupplierDirectory.SupplierView supplier =
                supplierDirectory.requireTradableSupplier(request.supplierId());
        OrganizationDirectory.LocationView delivery =
                organizationDirectory.requireLocation(request.deliveryLocationId());
        if (!delivery.canHoldStock()) {
            throw new ValidationException("Delivery location cannot hold stock");
        }
        if (!delivery.branchId().equals(request.branchId())) {
            throw new ValidationException("Delivery location does not belong to the ordering branch");
        }
        SecurityUtils.requireBranchAccess(request.branchId());

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNumber(CodeGenerator.reference("PO"));
        order.setSupplierId(supplier.id());
        order.setBranchId(request.branchId());
        order.setDeliveryLocationId(delivery.id());
        order.setRequisitionId(request.requisitionId());
        order.setOrderDate(LocalDate.now());
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setNotes(request.notes());
        order.setStatus(PurchaseOrderStatus.PENDING_APPROVAL);
        order.setExternalReference(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);
        order.setCurrency(StringUtils.hasText(request.currency())
                ? request.currency().toUpperCase() : supplier.currency());

        BigDecimal estimatedTotal = BigDecimal.ZERO;
        for (PurchaseOrderRequest.Line line : request.lines()) {
            ProductCatalog.ProductView product = productCatalog.requireProduct(line.productId());

            PurchaseOrderLine entity = new PurchaseOrderLine();
            entity.setProductId(product.id());
            entity.setMetalId(line.metalId() != null ? line.metalId() : product.defaultMetalId());
            entity.setPurityId(line.purityId() != null ? line.purityId() : product.defaultPurityId());
            entity.setOrderedQuantity(line.orderedQuantity());
            entity.setEstimatedWeight(line.estimatedWeight());
            entity.setRatePerGram(line.ratePerGram());
            entity.setMakingChargePerUnit(line.makingChargePerUnit());
            entity.setNotes(line.notes());

            // Indicative only: the payable amount comes from the supplier invoice.
            BigDecimal metal = MoneyUtils.nullSafe(line.estimatedWeight())
                    .multiply(MoneyUtils.nullSafe(line.ratePerGram()));
            BigDecimal making = MoneyUtils.nullSafe(line.makingChargePerUnit())
                    .multiply(BigDecimal.valueOf(line.orderedQuantity()));
            entity.setLineTotal(MoneyUtils.money(metal.add(making)));
            estimatedTotal = estimatedTotal.add(entity.getLineTotal());

            order.addLine(entity);
        }
        order.setEstimatedTotal(MoneyUtils.money(estimatedTotal));

        PurchaseOrder saved = orderRepository.save(order);

        if (request.requisitionId() != null) {
            requisitionRepository.findById(request.requisitionId())
                    .ifPresent(r -> r.setStatus(RequisitionStatus.ORDERED));
        }

        auditService.record("PURCHASE_ORDER_CREATED", "PurchaseOrder", saved.getId(), null,
                PurchaseOrderResponse.from(saved), saved.getBranchId());
        return PurchaseOrderResponse.from(saved);
    }

    @Transactional
    public PurchaseOrderResponse approveOrder(UUID id) {
        PurchaseOrder order = requireOrder(id);
        if (order.getStatus() != PurchaseOrderStatus.PENDING_APPROVAL) {
            throw new ConflictException("Purchase order " + order.getOrderNumber() + " is "
                    + order.getStatus() + " and cannot be approved");
        }
        // Re-checked at approval: a supplier may have been blocked since the order was raised.
        supplierDirectory.requireTradableSupplier(order.getSupplierId());

        order.setStatus(PurchaseOrderStatus.APPROVED);
        order.setApprovedBy(SecurityUtils.currentUsername().orElse("system"));
        order.setApprovedAt(Instant.now());

        auditService.record("PURCHASE_ORDER_APPROVED", "PurchaseOrder", id, null,
                Map.of("estimatedTotal", String.valueOf(order.getEstimatedTotal())),
                order.getBranchId());
        return PurchaseOrderResponse.from(order);
    }

    @Transactional
    public PurchaseOrderResponse rejectOrder(UUID id, String reason) {
        PurchaseOrder order = requireOrder(id);
        if (order.getStatus() != PurchaseOrderStatus.PENDING_APPROVAL) {
            throw new ConflictException("Only a pending purchase order can be rejected");
        }
        order.setStatus(PurchaseOrderStatus.REJECTED);
        order.setRejectionReason(reason);
        auditService.record("PURCHASE_ORDER_REJECTED", "PurchaseOrder", id, null,
                Map.of("reason", String.valueOf(reason)), order.getBranchId());
        return PurchaseOrderResponse.from(order);
    }

    @Transactional
    public PurchaseOrderResponse cancelOrder(UUID id, String reason) {
        PurchaseOrder order = requireOrder(id);
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED
                || order.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new ConflictException(
                    "An order with goods already received cannot be cancelled; close it instead");
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.setRejectionReason(reason);
        auditService.record("PURCHASE_ORDER_CANCELLED", "PurchaseOrder", id, null,
                Map.of("reason", String.valueOf(reason)), order.getBranchId());
        return PurchaseOrderResponse.from(order);
    }

    /** Closes an order that will not be fully delivered, e.g. a short shipment. */
    @Transactional
    public PurchaseOrderResponse closeOrder(UUID id, String reason) {
        PurchaseOrder order = requireOrder(id);
        if (!order.getStatus().acceptsReceipt()) {
            throw new ConflictException("Only an open order can be closed");
        }
        order.setStatus(PurchaseOrderStatus.CLOSED);
        auditService.record("PURCHASE_ORDER_CLOSED", "PurchaseOrder", id, null,
                Map.of("reason", String.valueOf(reason)), order.getBranchId());
        return PurchaseOrderResponse.from(order);
    }

    // ---------- helpers ----------

    private PurchaseOrder requireOrder(UUID id) {
        return orderRepository.findWithLinesById(id)
                .orElseThrow(() -> NotFoundException.of("PurchaseOrder", id));
    }

    private PurchaseRequisition requireRequisition(UUID id) {
        return requisitionRepository.findWithLinesById(id)
                .orElseThrow(() -> NotFoundException.of("PurchaseRequisition", id));
    }
}
