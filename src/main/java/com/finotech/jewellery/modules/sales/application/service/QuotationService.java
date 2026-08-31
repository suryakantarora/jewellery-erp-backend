package com.finotech.jewellery.modules.sales.application.service;

import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.pricing.application.PricingCalculator;
import com.finotech.jewellery.modules.sales.api.request.QuotationRequest;
import com.finotech.jewellery.modules.sales.api.response.QuotationResponse;
import com.finotech.jewellery.modules.sales.domain.entity.Quotation;
import com.finotech.jewellery.modules.sales.domain.entity.QuotationLine;
import com.finotech.jewellery.modules.sales.domain.enums.QuotationStatus;
import com.finotech.jewellery.modules.sales.infrastructure.repository.QuotationRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quotations: a priced offer with an expiry.
 *
 * <p>A quotation reserves nothing. It records the prices it was built from so
 * the customer can be shown the same figures again, but it must be re-priced
 * once expired because metal rates move daily.
 */
@Service
@RequiredArgsConstructor
public class QuotationService {

    private static final int DEFAULT_VALIDITY_DAYS = 7;

    private final QuotationRepository quotationRepository;
    private final PricingCalculator pricingCalculator;
    private final CustomerDirectory customerDirectory;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<QuotationResponse> search(QuotationStatus status, UUID customerId,
                                                  UUID branchId, Pageable pageable) {
        return PageResponse.of(quotationRepository.search(status, customerId, branchId, pageable),
                QuotationResponse::from);
    }

    @Transactional(readOnly = true)
    public QuotationResponse get(UUID id) {
        return QuotationResponse.from(requireQuotation(id));
    }

    @Transactional
    public QuotationResponse create(QuotationRequest request) {
        CustomerDirectory.CustomerView customer =
                customerDirectory.requireTransactableCustomer(request.customerId());
        SecurityUtils.requireBranchAccess(request.branchId());

        Quotation quotation = new Quotation();
        quotation.setQuotationNumber(CodeGenerator.reference("QT"));
        quotation.setCustomerId(customer.id());
        quotation.setBranchId(request.branchId());
        quotation.setQuotationDate(LocalDate.now());
        quotation.setValidUntil(LocalDate.now().plusDays(
                request.validForDays() == null ? DEFAULT_VALIDITY_DAYS : request.validForDays()));
        quotation.setNotes(request.notes());
        quotation.setStatus(QuotationStatus.ISSUED);

        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (UUID itemId : request.jewelleryItemIds().stream().distinct().toList()) {
            PricingCalculator.PriceBreakdown price = pricingCalculator.calculate(
                    new PricingCalculator.PriceRequest(itemId, customer.id(), request.branchId(),
                            null, null, false));

            QuotationLine line = new QuotationLine();
            line.setJewelleryItemId(itemId);
            line.setItemCode(price.itemCode());
            line.setMetalValue(price.metalValue());
            line.setWastageValue(price.wastageValue());
            line.setMakingCharge(price.makingCharge());
            line.setStoneValue(price.stoneValue());
            line.setTaxAmount(price.taxTotal());
            line.setLineTotal(price.finalPrice());
            quotation.addLine(line);

            subTotal = subTotal.add(price.subTotal());
            taxTotal = taxTotal.add(price.taxTotal());
            total = total.add(price.finalPrice());
            quotation.setCurrency(price.currency());
        }

        quotation.setSubTotal(MoneyUtils.money(subTotal));
        quotation.setTaxTotal(MoneyUtils.money(taxTotal));
        quotation.setTotalAmount(MoneyUtils.money(total));

        Quotation saved = quotationRepository.save(quotation);
        auditService.record("QUOTATION_CREATED", "Quotation", saved.getId(), null,
                java.util.Map.of("total", saved.getTotalAmount(), "items", saved.getLines().size()),
                saved.getBranchId());
        return QuotationResponse.from(saved);
    }

    @Transactional
    public QuotationResponse cancel(UUID id, String reason) {
        Quotation quotation = requireQuotation(id);
        if (quotation.getStatus() == QuotationStatus.CONVERTED) {
            throw new ConflictException("A converted quotation cannot be cancelled");
        }
        quotation.setStatus(QuotationStatus.CANCELLED);
        auditService.record("QUOTATION_CANCELLED", "Quotation", id, null,
                java.util.Map.of("reason", String.valueOf(reason)), quotation.getBranchId());
        return QuotationResponse.from(quotation);
    }

    /** Called by the sale service when a quotation becomes an order. */
    @Transactional
    public void markConverted(UUID quotationId, UUID saleId) {
        Quotation quotation = requireQuotation(quotationId);
        if (quotation.isExpired()) {
            throw new ConflictException("Quotation " + quotation.getQuotationNumber()
                    + " expired on " + quotation.getValidUntil() + " and must be re-priced");
        }
        if (quotation.getStatus() == QuotationStatus.CONVERTED) {
            throw new ConflictException("Quotation " + quotation.getQuotationNumber()
                    + " has already been converted");
        }
        quotation.setStatus(QuotationStatus.CONVERTED);
        quotation.setConvertedSaleId(saleId);
    }

    private Quotation requireQuotation(UUID id) {
        return quotationRepository.findWithLinesById(id)
                .orElseThrow(() -> NotFoundException.of("Quotation", id));
    }
}
