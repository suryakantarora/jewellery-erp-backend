package com.finotech.jewellery.modules.procurement.application.service;

import com.finotech.jewellery.modules.procurement.api.request.SupplierInvoiceRequest;
import com.finotech.jewellery.modules.procurement.api.response.ProcurementResponses.SupplierInvoiceResponse;
import com.finotech.jewellery.modules.procurement.domain.entity.SupplierInvoice;
import com.finotech.jewellery.modules.procurement.infrastructure.repository.SupplierInvoiceRepository;
import com.finotech.jewellery.modules.supplier.application.SupplierDirectory;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Supplier invoices — the payable side of procurement. Settlement itself is a
 * Finance concern; this module only records what is owed.
 */
@Service
@RequiredArgsConstructor
public class SupplierInvoiceService {

    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierDirectory supplierDirectory;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<SupplierInvoiceResponse> search(UUID supplierId, String status,
                                                        Pageable pageable) {
        return PageResponse.of(invoiceRepository.search(supplierId, status, pageable),
                SupplierInvoiceResponse::from);
    }

    @Transactional(readOnly = true)
    public SupplierInvoiceResponse get(UUID id) {
        return SupplierInvoiceResponse.from(invoiceRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("SupplierInvoice", id)));
    }

    @Transactional
    public SupplierInvoiceResponse create(SupplierInvoiceRequest request) {
        SupplierDirectory.SupplierView supplier =
                supplierDirectory.requireSupplier(request.supplierId());

        // A supplier's own invoice number must be unique for that supplier, which
        // is what stops the same bill being booked twice.
        if (invoiceRepository.existsBySupplierIdAndInvoiceNumberIgnoreCase(
                supplier.id(), request.invoiceNumber())) {
            throw new ConflictException("Invoice " + request.invoiceNumber()
                    + " is already recorded for this supplier");
        }

        SupplierInvoice invoice = new SupplierInvoice();
        invoice.setSupplierId(supplier.id());
        invoice.setInvoiceNumber(request.invoiceNumber().trim());
        invoice.setPurchaseOrderId(request.purchaseOrderId());
        invoice.setGoodsReceiptId(request.goodsReceiptId());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setCurrency(StringUtils.hasText(request.currency())
                ? request.currency().toUpperCase() : supplier.currency());
        invoice.setSubTotal(MoneyUtils.money(request.subTotal()));
        invoice.setTaxAmount(MoneyUtils.money(MoneyUtils.nullSafe(request.taxAmount())));
        invoice.setTotalAmount(MoneyUtils.money(
                request.subTotal().add(MoneyUtils.nullSafe(request.taxAmount()))));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setNotes(request.notes());

        // Fall back to the supplier's agreed terms when no due date is given.
        LocalDate dueDate = request.dueDate();
        if (dueDate == null && supplier.paymentTermsDays() != null) {
            dueDate = request.invoiceDate().plusDays(supplier.paymentTermsDays());
        }
        invoice.setDueDate(dueDate);

        SupplierInvoice saved = invoiceRepository.save(invoice);
        auditService.record("SUPPLIER_INVOICE_CREATED", "SupplierInvoice", saved.getId(), null,
                SupplierInvoiceResponse.from(saved));
        return SupplierInvoiceResponse.from(saved);
    }
}
