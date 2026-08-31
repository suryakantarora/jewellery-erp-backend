package com.finotech.jewellery.modules.exchange.api.response;

import com.finotech.jewellery.modules.exchange.domain.entity.ExchangeIntake;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeStatus;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ExchangeResponse(UUID id, String referenceNumber, ExchangeType exchangeType,
                               ExchangeStatus status, Set<ExchangeStatus> allowedTransitions,
                               UUID customerId, UUID branchId, UUID locationId,
                               LocalDate receivedDate, String description, int itemCount,
                               UUID originalItemId, UUID metalId, BigDecimal grossWeight,
                               BigDecimal stoneWeight, BigDecimal netWeight, String weighedBy,
                               UUID declaredPurityId, UUID testedPurityId,
                               BigDecimal testedFineness, String testMethod, String testedBy,
                               UUID rateId, BigDecimal ratePerUnit, BigDecimal pureWeight,
                               BigDecimal grossValuation, BigDecimal deductionPercentage,
                               BigDecimal deductionAmount, BigDecimal netValuation,
                               String currency, String valuedBy, String approvedBy,
                               Instant approvedAt, String rejectionReason, UUID appliedSaleId,
                               UUID scrapBatchId, Instant completedAt, String notes) {

    public static ExchangeResponse from(ExchangeIntake e) {
        return new ExchangeResponse(e.getId(), e.getReferenceNumber(), e.getExchangeType(),
                e.getStatus(), e.getStatus().allowedTransitions(), e.getCustomerId(),
                e.getBranchId(), e.getLocationId(), e.getReceivedDate(), e.getDescription(),
                e.getItemCount(), e.getOriginalItemId(), e.getMetalId(), e.getGrossWeight(),
                e.getStoneWeight(), e.getNetWeight(), e.getWeighedBy(), e.getDeclaredPurityId(),
                e.getTestedPurityId(), e.getTestedFineness(), e.getTestMethod(), e.getTestedBy(),
                e.getRateId(), e.getRatePerUnit(), e.getPureWeight(), e.getGrossValuation(),
                e.getDeductionPercentage(), e.getDeductionAmount(), e.getNetValuation(),
                e.getCurrency(), e.getValuedBy(), e.getApprovedBy(), e.getApprovedAt(),
                e.getRejectionReason(), e.getAppliedSaleId(), e.getScrapBatchId(),
                e.getCompletedAt(), e.getNotes());
    }
}
