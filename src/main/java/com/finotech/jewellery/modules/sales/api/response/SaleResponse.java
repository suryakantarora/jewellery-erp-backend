package com.finotech.jewellery.modules.sales.api.response;

import com.finotech.jewellery.modules.sales.domain.entity.Sale;
import com.finotech.jewellery.modules.sales.domain.enums.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaleResponse(UUID id, String saleNumber, String invoiceNumber, UUID customerId,
                           UUID branchId, UUID locationId, UUID quotationId, SaleStatus status,
                           LocalDate saleDate, String currency, BigDecimal subTotal,
                           BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal exchangeCredit,
                           long loyaltyPointsRedeemed, BigDecimal loyaltyRedemptionValue,
                           BigDecimal totalAmount, BigDecimal amountPayable, BigDecimal paidAmount,
                           BigDecimal outstandingAmount, BigDecimal refundedAmount,
                           UUID salespersonId, String discountApprovedBy, Instant confirmedAt,
                           Instant deliveredAt, String notes, List<SaleLineResponse> lines,
                           Instant createdAt, String createdBy) {

    public record SaleLineResponse(UUID id, UUID jewelleryItemId, String itemCode,
                                   UUID metalRateId, BigDecimal metalRatePerUnit,
                                   BigDecimal netMetalWeight, BigDecimal metalValue,
                                   BigDecimal wastageValue, BigDecimal makingCharge,
                                   BigDecimal stoneValue, String loyaltyTierCode,
                                   BigDecimal tierDiscountAmount, BigDecimal discountAmount,
                                   BigDecimal taxAmount, BigDecimal lineTotal, boolean returned) {
    }

    public static SaleResponse from(Sale s) {
        List<SaleLineResponse> lines = s.getLines().stream()
                .map(l -> new SaleLineResponse(l.getId(), l.getJewelleryItemId(), l.getItemCode(),
                        l.getMetalRateId(), l.getMetalRatePerUnit(), l.getNetMetalWeight(),
                        l.getMetalValue(), l.getWastageValue(), l.getMakingCharge(),
                        l.getStoneValue(), l.getLoyaltyTierCode(), l.getTierDiscountAmount(),
                        l.getDiscountAmount(), l.getTaxAmount(),
                        l.getLineTotal(), l.isReturned()))
                .toList();
        return new SaleResponse(s.getId(), s.getSaleNumber(), s.getInvoiceNumber(),
                s.getCustomerId(), s.getBranchId(), s.getLocationId(), s.getQuotationId(),
                s.getStatus(), s.getSaleDate(), s.getCurrency(), s.getSubTotal(),
                s.getDiscountTotal(), s.getTaxTotal(), s.getExchangeCredit(),
                s.getLoyaltyPointsRedeemed(), s.getLoyaltyRedemptionValue(), s.getTotalAmount(),
                s.amountPayable(), s.getPaidAmount(), s.outstandingAmount(), s.getRefundedAmount(),
                s.getSalespersonId(), s.getDiscountApprovedBy(), s.getConfirmedAt(),
                s.getDeliveredAt(), s.getNotes(), lines, s.getCreatedAt(), s.getCreatedBy());
    }
}
