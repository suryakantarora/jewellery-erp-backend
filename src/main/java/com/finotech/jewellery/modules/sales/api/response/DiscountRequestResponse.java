package com.finotech.jewellery.modules.sales.api.response;

import com.finotech.jewellery.modules.sales.domain.entity.DiscountRequest;
import com.finotech.jewellery.modules.sales.domain.enums.DiscountRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DiscountRequestResponse(UUID id, UUID branchId, UUID customerId,
                                      UUID jewelleryItemId, UUID quotationId,
                                      BigDecimal requestedPercentage, BigDecimal requestedAmount,
                                      String currency, String reason,
                                      DiscountRequestStatus status, String requestedBy,
                                      Instant requestedAt, String decidedBy, Instant decidedAt,
                                      String decisionNote, Instant expiresAt,
                                      UUID consumedBySaleId) {

    public static DiscountRequestResponse from(DiscountRequest d) {
        return new DiscountRequestResponse(d.getId(), d.getBranchId(), d.getCustomerId(),
                d.getJewelleryItemId(), d.getQuotationId(), d.getRequestedPercentage(),
                d.getRequestedAmount(), d.getCurrency(), d.getReason(), d.getStatus(),
                d.getCreatedBy(), d.getCreatedAt(), d.getDecidedBy(), d.getDecidedAt(),
                d.getDecisionNote(), d.getExpiresAt(), d.getConsumedBySaleId());
    }
}
