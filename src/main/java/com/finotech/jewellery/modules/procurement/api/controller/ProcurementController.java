package com.finotech.jewellery.modules.procurement.api.controller;

import com.finotech.jewellery.modules.procurement.api.request.GoodsReceiptRequest;
import com.finotech.jewellery.modules.procurement.api.request.PurchaseOrderRequest;
import com.finotech.jewellery.modules.procurement.api.request.RequisitionRequest;
import com.finotech.jewellery.modules.procurement.api.request.SupplierInvoiceRequest;
import com.finotech.jewellery.modules.procurement.api.response.ProcurementResponses.GoodsReceiptResponse;
import com.finotech.jewellery.modules.procurement.api.response.ProcurementResponses.PurchaseOrderResponse;
import com.finotech.jewellery.modules.procurement.api.response.ProcurementResponses.RequisitionResponse;
import com.finotech.jewellery.modules.procurement.api.response.ProcurementResponses.SupplierInvoiceResponse;
import com.finotech.jewellery.modules.procurement.application.service.GoodsReceiptService;
import com.finotech.jewellery.modules.procurement.application.service.PurchaseOrderService;
import com.finotech.jewellery.modules.procurement.application.service.SupplierInvoiceService;
import com.finotech.jewellery.modules.procurement.domain.enums.GoodsReceiptStatus;
import com.finotech.jewellery.modules.procurement.domain.enums.PurchaseOrderStatus;
import com.finotech.jewellery.modules.procurement.domain.enums.RequisitionStatus;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Procurement")
@RestController
@RequestMapping("/api/v1/procurement")
@RequiredArgsConstructor
public class ProcurementController {

    private static final String VIEW = "hasAuthority('PROCUREMENT_VIEW')";
    private static final String CREATE = "hasAuthority('PROCUREMENT_CREATE')";
    private static final String APPROVE = "hasAuthority('PROCUREMENT_APPROVE')";
    private static final String RECEIVE = "hasAuthority('PROCUREMENT_RECEIVE')";

    private final PurchaseOrderService orderService;
    private final GoodsReceiptService receiptService;
    private final SupplierInvoiceService invoiceService;

    // ---------- requisitions ----------

    @Operation(summary = "Search purchase requisitions")
    @GetMapping("/requisitions")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<RequisitionResponse>>> searchRequisitions(
            @RequestParam(required = false) RequisitionStatus status,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.searchRequisitions(status, branchId, pageable)));
    }

    @Operation(summary = "Get a requisition")
    @GetMapping("/requisitions/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<RequisitionResponse>> getRequisition(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getRequisition(id)));
    }

    @Operation(summary = "Raise a purchase requisition")
    @PostMapping("/requisitions")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<RequisitionResponse>> createRequisition(
            @Valid @RequestBody RequisitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(orderService.createRequisition(request)));
    }

    @Operation(summary = "Approve a requisition")
    @PostMapping("/requisitions/{id}/approve")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<RequisitionResponse>> approveRequisition(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.approveRequisition(id)));
    }

    @Operation(summary = "Reject a requisition")
    @PostMapping("/requisitions/{id}/reject")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<RequisitionResponse>> rejectRequisition(
            @PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.rejectRequisition(id, reason)));
    }

    // ---------- purchase orders ----------

    @Operation(summary = "Search purchase orders")
    @GetMapping("/purchase-orders")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<PurchaseOrderResponse>>> searchOrders(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20, sort = "orderDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.searchOrders(status, supplierId, branchId, pageable)));
    }

    @Operation(summary = "Get a purchase order")
    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrder(id)));
    }

    @Operation(summary = "Raise a purchase order",
            description = "Send X-Idempotency-Key so a retried request does not duplicate "
                    + "a commitment to the supplier.")
    @PostMapping("/purchase-orders")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createOrder(
            @Valid @RequestBody PurchaseOrderRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(orderService.createOrder(request, idempotencyKey)));
    }

    @Operation(summary = "Approve a purchase order")
    @PostMapping("/purchase-orders/{id}/approve")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> approveOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.approveOrder(id)));
    }

    @Operation(summary = "Reject a purchase order")
    @PostMapping("/purchase-orders/{id}/reject")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> rejectOrder(
            @PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.rejectOrder(id, reason)));
    }

    @Operation(summary = "Cancel a purchase order")
    @PostMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancelOrder(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.cancelOrder(id, reason)));
    }

    @Operation(summary = "Close a short-delivered purchase order")
    @PostMapping("/purchase-orders/{id}/close")
    @PreAuthorize(APPROVE)
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> closeOrder(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.closeOrder(id, reason)));
    }

    // ---------- goods receipts ----------

    @Operation(summary = "Search goods receipts")
    @GetMapping("/goods-receipts")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<GoodsReceiptResponse>>> searchReceipts(
            @RequestParam(required = false) GoodsReceiptStatus status,
            @RequestParam(required = false) UUID purchaseOrderId,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20, sort = "receiptDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                receiptService.search(status, purchaseOrderId, branchId, pageable)));
    }

    @Operation(summary = "Get a goods receipt")
    @GetMapping("/goods-receipts/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> getReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(receiptService.get(id)));
    }

    @Operation(summary = "Record a delivery against a purchase order",
            description = "One line per physical piece. Nothing enters stock until the "
                    + "receipt is accepted.")
    @PostMapping("/goods-receipts")
    @PreAuthorize(RECEIVE)
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> createReceipt(
            @Valid @RequestBody GoodsReceiptRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(receiptService.create(request, idempotencyKey)));
    }

    @Operation(summary = "Pass quality check and create the serialized items")
    @PostMapping("/goods-receipts/{id}/accept")
    @PreAuthorize(RECEIVE)
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> acceptReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(receiptService.accept(id)));
    }

    @Operation(summary = "Fail quality check; no stock is created")
    @PostMapping("/goods-receipts/{id}/reject")
    @PreAuthorize(RECEIVE)
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> rejectReceipt(
            @PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.ok(receiptService.reject(id, reason)));
    }

    // ---------- supplier invoices ----------

    @Operation(summary = "Search supplier invoices")
    @GetMapping("/supplier-invoices")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<SupplierInvoiceResponse>>> searchInvoices(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                invoiceService.search(supplierId, status, pageable)));
    }

    @Operation(summary = "Record a supplier invoice")
    @PostMapping("/supplier-invoices")
    @PreAuthorize(CREATE)
    public ResponseEntity<ApiResponse<SupplierInvoiceResponse>> createInvoice(
            @Valid @RequestBody SupplierInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(invoiceService.create(request)));
    }
}
