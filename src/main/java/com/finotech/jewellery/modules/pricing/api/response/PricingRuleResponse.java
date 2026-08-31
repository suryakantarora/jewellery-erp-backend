package com.finotech.jewellery.modules.pricing.api.response;

import com.finotech.jewellery.modules.pricing.domain.entity.DiscountPolicy;
import com.finotech.jewellery.modules.pricing.domain.entity.MakingChargeRule;
import com.finotech.jewellery.modules.pricing.domain.entity.TaxRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One shape for the three pricing configuration entities. */
public record PricingRuleResponse(UUID id, String code, String name, String chargeType,
                                  BigDecimal value, BigDecimal wastagePercentage,
                                  UUID productId, UUID productTypeId, UUID metalId, UUID purityId,
                                  UUID branchId, Boolean inclusive,
                                  BigDecimal maxPercentageWithoutApproval,
                                  BigDecimal maxPercentageWithApproval,
                                  LocalDate effectiveFrom, LocalDate effectiveTo,
                                  Integer priority, boolean active) {

    public static PricingRuleResponse from(MakingChargeRule r) {
        return new PricingRuleResponse(r.getId(), r.getCode(), r.getName(),
                r.getChargeType().name(), r.getChargeValue(), r.getWastagePercentage(),
                r.getProductId(), r.getProductTypeId(), r.getMetalId(), r.getPurityId(),
                r.getBranchId(), null, null, null, r.getEffectiveFrom(), r.getEffectiveTo(),
                r.getPriority(), r.isActive());
    }

    public static PricingRuleResponse from(TaxRate t) {
        return new PricingRuleResponse(t.getId(), t.getCode(), t.getName(), "TAX",
                t.getPercentage(), null, null, t.getProductTypeId(), null, null, t.getBranchId(),
                t.isInclusive(), null, null, t.getEffectiveFrom(), t.getEffectiveTo(), null,
                t.isActive());
    }

    public static PricingRuleResponse from(DiscountPolicy p) {
        return new PricingRuleResponse(p.getId(), p.getCode(), p.getName(), "DISCOUNT_POLICY",
                null, null, null, null, null, null, p.getBranchId(), null,
                p.getMaxPercentageWithoutApproval(), p.getMaxPercentageWithApproval(),
                p.getEffectiveFrom(), p.getEffectiveTo(), null, p.isActive());
    }
}
