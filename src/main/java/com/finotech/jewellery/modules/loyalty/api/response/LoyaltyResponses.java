package com.finotech.jewellery.modules.loyalty.api.response;

import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyAccount;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyProgram;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyTransaction;
import com.finotech.jewellery.modules.loyalty.domain.enums.LoyaltyTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LoyaltyResponses {

    private LoyaltyResponses() {
    }

    public record ProgramResponse(UUID id, String code, String name, UUID companyId,
                                  BigDecimal pointsPerCurrencyUnit,
                                  BigDecimal currencyValuePerPoint, Integer pointsValidityMonths,
                                  Integer minimumRedeemablePoints, boolean earnOnMakingChargeOnly,
                                  LocalDate effectiveFrom, LocalDate effectiveTo, boolean active,
                                  List<TierResponse> tiers) {

        public static ProgramResponse from(LoyaltyProgram p) {
            return new ProgramResponse(p.getId(), p.getCode(), p.getName(), p.getCompanyId(),
                    p.getPointsPerCurrencyUnit(), p.getCurrencyValuePerPoint(),
                    p.getPointsValidityMonths(), p.getMinimumRedeemablePoints(),
                    p.isEarnOnMakingChargeOnly(), p.getEffectiveFrom(), p.getEffectiveTo(),
                    p.isActive(),
                    p.getTiers().stream().map(t -> new TierResponse(t.getId(), t.getCode(),
                            t.getName(), t.getMinimumPoints(), t.getEarnMultiplier(),
                            t.getDiscountPercentage(), t.getBenefits())).toList());
        }
    }

    public record TierResponse(UUID id, String code, String name, long minimumPoints,
                               BigDecimal earnMultiplier, BigDecimal discountPercentage,
                               String benefits) {
    }

    public record AccountResponse(UUID id, UUID customerId, UUID programId, String programCode,
                                  String tierCode, String tierName, long pointsBalance,
                                  long lifetimePoints, long pointsRedeemed, long pointsExpired,
                                  BigDecimal redeemableValue, Instant enrolledAt,
                                  Instant lastActivityAt, boolean active) {

        public static AccountResponse from(LoyaltyAccount a, BigDecimal redeemableValue) {
            return new AccountResponse(a.getId(), a.getCustomerId(), a.getProgram().getId(),
                    a.getProgram().getCode(),
                    a.getTier() == null ? null : a.getTier().getCode(),
                    a.getTier() == null ? null : a.getTier().getName(),
                    a.getPointsBalance(), a.getLifetimePoints(), a.getPointsRedeemed(),
                    a.getPointsExpired(), redeemableValue, a.getEnrolledAt(),
                    a.getLastActivityAt(), a.isActive());
        }
    }

    public record TransactionResponse(UUID id, UUID customerId, LoyaltyTransactionType type,
                                      long points, long balanceAfter, BigDecimal monetaryValue,
                                      String referenceType, String referenceId, LocalDate expiresOn,
                                      boolean expired, String reason, Instant occurredAt) {

        public static TransactionResponse from(LoyaltyTransaction t) {
            return new TransactionResponse(t.getId(), t.getCustomerId(), t.getTransactionType(),
                    t.getPoints(), t.getBalanceAfter(), t.getMonetaryValue(), t.getReferenceType(),
                    t.getReferenceId(), t.getExpiresOn(), t.isExpired(), t.getReason(),
                    t.getOccurredAt());
        }
    }
}
