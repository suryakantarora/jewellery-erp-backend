package com.finotech.jewellery.modules.pricing.application.service;

import com.finotech.jewellery.modules.inventory.application.InventoryOperations;
import com.finotech.jewellery.modules.loyalty.application.LoyaltyDirectory;
import com.finotech.jewellery.modules.loyalty.application.LoyaltyTierBenefits;
import com.finotech.jewellery.modules.metal.application.MetalRateProvider;
import com.finotech.jewellery.modules.metal.domain.enums.RateType;
import com.finotech.jewellery.modules.pricing.application.PricingCalculator;
import com.finotech.jewellery.modules.pricing.domain.entity.DiscountPolicy;
import com.finotech.jewellery.modules.pricing.domain.entity.MakingChargeRule;
import com.finotech.jewellery.modules.pricing.domain.entity.TaxRate;
import com.finotech.jewellery.modules.pricing.domain.enums.ChargeType;
import com.finotech.jewellery.modules.pricing.domain.enums.DiscountType;
import com.finotech.jewellery.modules.pricing.infrastructure.repository.DiscountPolicyRepository;
import com.finotech.jewellery.modules.pricing.infrastructure.repository.MakingChargeRuleRepository;
import com.finotech.jewellery.modules.pricing.infrastructure.repository.TaxRateRepository;
import com.finotech.jewellery.modules.product.application.ProductCatalog;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The pricing engine.
 *
 * <pre>
 *   metal value    = net metal weight x rate for that metal/purity
 * + wastage        = net metal weight x wastage% x rate
 * + making charge  = per the matched rule (per gram, % of metal, or flat)
 * + stone value    = captured on the item
 * = sub total
 * + tax            = applicable tax rates on the sub total
 * - discount       = within the branch's policy, or approved
 * = final price
 * </pre>
 *
 * <p>Pricing is deliberately independent of Sales (dependency rule 5): it reads
 * the item, the rate and the rules, and returns a breakdown. It writes nothing.
 */
@Service
@RequiredArgsConstructor
public class PricingService implements PricingCalculator {

    private static final int PERCENT_SCALE = 10;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final InventoryOperations inventory;
    private final MetalRateProvider metalRates;
    private final ProductCatalog productCatalog;
    private final MakingChargeRuleRepository makingChargeRules;
    private final TaxRateRepository taxRates;
    private final DiscountPolicyRepository discountPolicies;
    private final LoyaltyTierBenefits loyaltyTierBenefits;

    @Override
    @Transactional(readOnly = true)
    public PriceBreakdown calculate(PriceRequest request) {
        InventoryOperations.ItemView item = inventory.requireItem(request.jewelleryItemId());
        ProductCatalog.ProductView product = productCatalog.requireProduct(item.productId());
        UUID branchId = request.branchId() != null ? request.branchId() : item.currentBranchId();
        LocalDate today = LocalDate.now();

        // ---- metal ----
        MetalRateProvider.RateView rate = metalRates.requireEffectiveRate(
                item.metalId(), item.purityId(), RateType.SELLING, today, branchId);
        BigDecimal netWeight = MoneyUtils.nullSafe(item.netMetalWeight());
        BigDecimal metalValue = netWeight.multiply(rate.ratePerUnit());

        // ---- making charge and wastage ----
        Optional<MakingChargeRule> rule = resolveRule(product, item, branchId, today);

        BigDecimal wastagePercentage = rule.map(MakingChargeRule::getWastagePercentage)
                .filter(v -> v != null)
                .orElseGet(() -> MoneyUtils.nullSafe(product.defaultWastagePercentage()));
        BigDecimal wastageWeight = netWeight
                .multiply(wastagePercentage)
                .divide(HUNDRED, MoneyUtils.WEIGHT_SCALE, RoundingMode.HALF_UP);
        BigDecimal wastageValue = wastageWeight.multiply(rate.ratePerUnit());

        MakingCharge making = makingCharge(rule, product, netWeight, metalValue);

        // ---- stones ----
        BigDecimal stoneValue = MoneyUtils.nullSafe(stoneValueOf(item));

        BigDecimal subTotal = metalValue
                .add(wastageValue)
                .add(making.amount())
                .add(stoneValue);

        // ---- discounts, before tax so tax is charged on what the customer pays ----

        // A tier discount is a standing entitlement, not a staff decision, so it
        // is applied automatically and does not consume the approval allowance.
        TierDiscount tierDiscount = tierDiscount(request, subTotal);

        Discount manual = applyDiscount(request, branchId, today, subTotal, making.amount(),
                metalValue);

        BigDecimal totalDiscount = tierDiscount.amount().add(manual.amount());
        if (totalDiscount.compareTo(subTotal) > 0) {
            throw new ValidationException(
                    "The tier entitlement and the requested discount together exceed the price");
        }
        BigDecimal taxableBase = subTotal.subtract(totalDiscount);

        // ---- tax ----
        List<TaxLine> taxLines = new ArrayList<>();
        BigDecimal addedTax = BigDecimal.ZERO;
        for (TaxRate tax : taxRates.findApplicable(branchId, product.productTypeId(), today)) {
            BigDecimal amount;
            if (tax.isInclusive()) {
                // The base already contains this tax; report the portion it represents.
                BigDecimal divisor = HUNDRED.add(tax.getPercentage());
                amount = taxableBase.multiply(tax.getPercentage())
                        .divide(divisor, MoneyUtils.MONEY_SCALE, RoundingMode.HALF_UP);
            } else {
                amount = taxableBase.multiply(tax.getPercentage())
                        .divide(HUNDRED, MoneyUtils.MONEY_SCALE, RoundingMode.HALF_UP);
                addedTax = addedTax.add(amount);
            }
            taxLines.add(new TaxLine(tax.getCode(), tax.getName(), tax.getPercentage(),
                    MoneyUtils.money(amount), tax.isInclusive()));
        }

        BigDecimal finalPrice = taxableBase.add(addedTax);

        return new PriceBreakdown(
                item.id(),
                item.itemCode(),
                rate.rateId(),
                MoneyUtils.money(rate.ratePerUnit()),
                MoneyUtils.weight(netWeight),
                MoneyUtils.money(metalValue),
                wastagePercentage,
                MoneyUtils.weight(wastageWeight),
                MoneyUtils.money(wastageValue),
                rule.map(MakingChargeRule::getCode).orElse(null),
                making.type(),
                making.rateValue(),
                MoneyUtils.money(making.amount()),
                MoneyUtils.money(stoneValue),
                MoneyUtils.money(subTotal),
                taxLines,
                MoneyUtils.money(taxLines.stream()
                        .filter(t -> !t.inclusive())
                        .map(TaxLine::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)),
                tierDiscount.tierCode(),
                tierDiscount.percentage(),
                MoneyUtils.money(tierDiscount.amount()),
                MoneyUtils.money(manual.amount()),
                MoneyUtils.money(totalDiscount),
                MoneyUtils.money(finalPrice),
                rate.currency(),
                manual.requiresApproval());
    }

    // ---------- making charge ----------

    private record MakingCharge(String type, BigDecimal rateValue, BigDecimal amount) {
    }

    private Optional<MakingChargeRule> resolveRule(ProductCatalog.ProductView product,
                                                   InventoryOperations.ItemView item,
                                                   UUID branchId, LocalDate onDate) {
        // Most specific scope wins; priority then code break remaining ties.
        return makingChargeRules.findCandidates(product.id(), product.productTypeId(),
                        item.metalId(), item.purityId(), branchId, onDate).stream()
                .max(Comparator.comparingInt(MakingChargeRule::specificity)
                        .thenComparingInt(MakingChargeRule::getPriority)
                        .thenComparing(MakingChargeRule::getCode));
    }

    private MakingCharge makingCharge(Optional<MakingChargeRule> rule,
                                      ProductCatalog.ProductView product,
                                      BigDecimal netWeight, BigDecimal metalValue) {
        ChargeType type;
        BigDecimal value;
        BigDecimal min = null;
        BigDecimal max = null;

        if (rule.isPresent()) {
            type = rule.get().getChargeType();
            value = rule.get().getChargeValue();
            min = rule.get().getMinCharge();
            max = rule.get().getMaxCharge();
        } else if (StringUtils.hasText(product.defaultMakingChargeType())
                && product.defaultMakingChargeValue() != null) {
            // No rule matched: fall back to the product's own default.
            type = ChargeType.valueOf(product.defaultMakingChargeType().toUpperCase());
            value = product.defaultMakingChargeValue();
        } else {
            return new MakingCharge(null, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal amount = switch (type) {
            case PER_GRAM -> netWeight.multiply(value);
            case PERCENTAGE_OF_METAL -> metalValue.multiply(value)
                    .divide(HUNDRED, MoneyUtils.MONEY_SCALE, RoundingMode.HALF_UP);
            case FLAT -> value;
        };

        if (min != null && amount.compareTo(min) < 0) {
            amount = min;
        }
        if (max != null && amount.compareTo(max) > 0) {
            amount = max;
        }
        return new MakingCharge(type.name(), value, amount);
    }

    // ---------- loyalty tier entitlement ----------

    private record TierDiscount(String tierCode, BigDecimal percentage, BigDecimal amount) {

        static TierDiscount none() {
            return new TierDiscount(null, null, BigDecimal.ZERO);
        }
    }

    /**
     * Resolves the standing discount the customer's loyalty tier grants.
     *
     * <p>Applied to the sub total rather than to the making charge alone: a tier
     * benefit is advertised to the customer as a discount on the price, and
     * quietly applying it to a fraction of that price would not match what they
     * were promised.
     */
    private TierDiscount tierDiscount(PriceRequest request, BigDecimal subTotal) {
        if (!request.applyTierDiscount() || request.customerId() == null) {
            return TierDiscount.none();
        }
        LoyaltyTierBenefits.TierBenefit benefit =
                loyaltyTierBenefits.benefitFor(request.customerId());
        if (benefit == null || benefit.discountPercentage() == null
                || benefit.discountPercentage().signum() <= 0) {
            return TierDiscount.none();
        }
        BigDecimal amount = subTotal.multiply(benefit.discountPercentage())
                .divide(HUNDRED, MoneyUtils.MONEY_SCALE, RoundingMode.HALF_UP);
        return new TierDiscount(benefit.tierCode(), benefit.discountPercentage(), amount);
    }

    // ---------- discount ----------

    private record Discount(BigDecimal amount, boolean requiresApproval) {
    }

    private Discount applyDiscount(PriceRequest request, UUID branchId, LocalDate onDate,
                                   BigDecimal subTotal, BigDecimal makingCharge,
                                   BigDecimal metalValue) {
        if (request.discountValue() == null || request.discountValue().signum() <= 0) {
            return new Discount(BigDecimal.ZERO, false);
        }
        DiscountType type = StringUtils.hasText(request.discountType())
                ? DiscountType.valueOf(request.discountType().toUpperCase())
                : DiscountType.PERCENTAGE;

        DiscountPolicy policy = discountPolicies.findApplicable(branchId, onDate).stream()
                .findFirst()
                .orElse(null);

        // The base a discount may erode depends on the policy: most shops let
        // staff discount the making charge but not the metal itself.
        BigDecimal discountableBase = subTotal;
        if (policy != null) {
            discountableBase = BigDecimal.ZERO;
            if (policy.isAppliesToMakingCharge()) {
                discountableBase = discountableBase.add(makingCharge);
            }
            if (policy.isAppliesToMetalValue()) {
                discountableBase = discountableBase.add(metalValue);
            }
        }

        BigDecimal requested = type == DiscountType.PERCENTAGE
                ? discountableBase.multiply(request.discountValue())
                        .divide(HUNDRED, MoneyUtils.MONEY_SCALE, RoundingMode.HALF_UP)
                : request.discountValue();

        if (requested.compareTo(discountableBase) > 0) {
            throw new ValidationException(
                    "Discount exceeds the discountable amount of " + MoneyUtils.money(discountableBase));
        }

        if (policy == null) {
            // With no policy configured, any discount is an exception that needs sign-off.
            if (!request.discountApproved()) {
                throw new ValidationException(
                        "No discount policy is configured for this branch; the discount needs approval");
            }
            return new Discount(requested, true);
        }

        BigDecimal requestedPercentage = discountableBase.signum() == 0
                ? BigDecimal.ZERO
                : requested.multiply(HUNDRED)
                        .divide(discountableBase, PERCENT_SCALE, RoundingMode.HALF_UP);

        if (requestedPercentage.compareTo(policy.getMaxPercentageWithApproval()) > 0) {
            throw new ValidationException("Discount of " + requestedPercentage.setScale(2,
                    RoundingMode.HALF_UP) + "% exceeds the maximum of "
                    + policy.getMaxPercentageWithApproval() + "% allowed even with approval");
        }

        boolean needsApproval =
                requestedPercentage.compareTo(policy.getMaxPercentageWithoutApproval()) > 0;
        if (needsApproval && !request.discountApproved()) {
            throw new ValidationException("Discount of " + requestedPercentage.setScale(2,
                    RoundingMode.HALF_UP) + "% exceeds the branch limit of "
                    + policy.getMaxPercentageWithoutApproval() + "% and requires approval");
        }
        return new Discount(requested, needsApproval);
    }

    // ---------- stones ----------

    /**
     * Stone value is captured on the item when it is created or re-costed, so
     * pricing uses the recorded figure rather than re-valuing stones at sale time.
     */
    private BigDecimal stoneValueOf(InventoryOperations.ItemView item) {
        return item.stoneValue();
    }
}
