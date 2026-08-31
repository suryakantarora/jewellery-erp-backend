package com.finotech.jewellery.modules.sales.api.response;

import com.finotech.jewellery.modules.sales.domain.entity.Quotation;
import com.finotech.jewellery.modules.sales.domain.enums.QuotationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotationResponse(UUID id, String quotationNumber, UUID customerId, UUID branchId,
                                QuotationStatus status, LocalDate quotationDate,
                                LocalDate validUntil, boolean expired, String currency,
                                BigDecimal subTotal, BigDecimal discountTotal, BigDecimal taxTotal,
                                BigDecimal totalAmount, UUID convertedSaleId, String notes,
                                List<QuotationLineResponse> lines) {

    public record QuotationLineResponse(UUID id, UUID jewelleryItemId, String itemCode,
                                        BigDecimal metalValue, BigDecimal wastageValue,
                                        BigDecimal makingCharge, BigDecimal stoneValue,
                                        BigDecimal taxAmount, BigDecimal lineTotal) {
    }

    public static QuotationResponse from(Quotation q) {
        List<QuotationLineResponse> lines = q.getLines().stream()
                .map(l -> new QuotationLineResponse(l.getId(), l.getJewelleryItemId(),
                        l.getItemCode(), l.getMetalValue(), l.getWastageValue(),
                        l.getMakingCharge(), l.getStoneValue(), l.getTaxAmount(), l.getLineTotal()))
                .toList();
        return new QuotationResponse(q.getId(), q.getQuotationNumber(), q.getCustomerId(),
                q.getBranchId(), q.getStatus(), q.getQuotationDate(), q.getValidUntil(),
                q.isExpired(), q.getCurrency(), q.getSubTotal(), q.getDiscountTotal(),
                q.getTaxTotal(), q.getTotalAmount(), q.getConvertedSaleId(), q.getNotes(), lines);
    }
}
