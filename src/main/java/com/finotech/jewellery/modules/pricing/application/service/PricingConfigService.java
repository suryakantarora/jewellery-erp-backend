package com.finotech.jewellery.modules.pricing.application.service;

import com.finotech.jewellery.modules.pricing.api.request.DiscountPolicyRequest;
import com.finotech.jewellery.modules.pricing.api.request.MakingChargeRuleRequest;
import com.finotech.jewellery.modules.pricing.api.request.TaxRateRequest;
import com.finotech.jewellery.modules.pricing.api.response.PricingRuleResponse;
import com.finotech.jewellery.modules.pricing.domain.entity.DiscountPolicy;
import com.finotech.jewellery.modules.pricing.domain.entity.MakingChargeRule;
import com.finotech.jewellery.modules.pricing.domain.entity.TaxRate;
import com.finotech.jewellery.modules.pricing.infrastructure.repository.DiscountPolicyRepository;
import com.finotech.jewellery.modules.pricing.infrastructure.repository.MakingChargeRuleRepository;
import com.finotech.jewellery.modules.pricing.infrastructure.repository.TaxRateRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the rules the pricing engine reads. Every change here moves money,
 * so all of it is audited and gated behind PRICE_CHANGE.
 */
@Service
@RequiredArgsConstructor
public class PricingConfigService {

    private final MakingChargeRuleRepository makingChargeRules;
    private final TaxRateRepository taxRates;
    private final DiscountPolicyRepository discountPolicies;
    private final AuditService auditService;

    // ---------- making charge rules ----------

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> listMakingChargeRules() {
        return makingChargeRules.findAllByOrderByPriorityDescCodeAsc().stream()
                .map(PricingRuleResponse::from).toList();
    }

    @Transactional
    public PricingRuleResponse createMakingChargeRule(MakingChargeRuleRequest request) {
        if (makingChargeRules.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Rule code already exists: " + request.code());
        }
        validateWindow(request.effectiveFrom(), request.effectiveTo());
        if (request.minCharge() != null && request.maxCharge() != null
                && request.minCharge().compareTo(request.maxCharge()) > 0) {
            throw new ValidationException("Minimum charge cannot exceed the maximum charge");
        }

        MakingChargeRule rule = new MakingChargeRule();
        rule.setCode(request.code().trim().toUpperCase());
        rule.setName(request.name().trim());
        rule.setProductId(request.productId());
        rule.setProductTypeId(request.productTypeId());
        rule.setMetalId(request.metalId());
        rule.setPurityId(request.purityId());
        rule.setBranchId(request.branchId());
        rule.setChargeType(request.chargeType());
        rule.setChargeValue(request.chargeValue());
        rule.setWastagePercentage(request.wastagePercentage());
        rule.setMinCharge(request.minCharge());
        rule.setMaxCharge(request.maxCharge());
        rule.setEffectiveFrom(request.effectiveFrom());
        rule.setEffectiveTo(request.effectiveTo());
        rule.setPriority(request.priority());

        MakingChargeRule saved = makingChargeRules.save(rule);
        auditService.record("MAKING_CHARGE_RULE_CREATED", "MakingChargeRule", saved.getId(), null,
                PricingRuleResponse.from(saved), saved.getBranchId());
        return PricingRuleResponse.from(saved);
    }

    /**
     * Retires a rule instead of deleting it, so a price computed yesterday can
     * still be explained from the rule that produced it.
     */
    @Transactional
    public PricingRuleResponse deactivateMakingChargeRule(UUID id) {
        MakingChargeRule rule = makingChargeRules.findById(id)
                .orElseThrow(() -> NotFoundException.of("MakingChargeRule", id));
        PricingRuleResponse before = PricingRuleResponse.from(rule);
        rule.setActive(false);
        auditService.record("MAKING_CHARGE_RULE_DEACTIVATED", "MakingChargeRule", id, before,
                PricingRuleResponse.from(rule), rule.getBranchId());
        return PricingRuleResponse.from(rule);
    }

    // ---------- tax ----------

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> listTaxRates() {
        return taxRates.findAllByOrderByCodeAsc().stream().map(PricingRuleResponse::from).toList();
    }

    @Transactional
    public PricingRuleResponse createTaxRate(TaxRateRequest request) {
        validateWindow(request.effectiveFrom(), request.effectiveTo());
        TaxRate tax = new TaxRate();
        tax.setCode(request.code().trim().toUpperCase());
        tax.setName(request.name().trim());
        tax.setPercentage(request.percentage());
        tax.setBranchId(request.branchId());
        tax.setProductTypeId(request.productTypeId());
        tax.setInclusive(request.inclusive());
        tax.setEffectiveFrom(request.effectiveFrom());
        tax.setEffectiveTo(request.effectiveTo());

        TaxRate saved = taxRates.save(tax);
        auditService.record("TAX_RATE_CREATED", "TaxRate", saved.getId(), null,
                PricingRuleResponse.from(saved), saved.getBranchId());
        return PricingRuleResponse.from(saved);
    }

    @Transactional
    public PricingRuleResponse deactivateTaxRate(UUID id) {
        TaxRate tax = taxRates.findById(id).orElseThrow(() -> NotFoundException.of("TaxRate", id));
        tax.setActive(false);
        auditService.record("TAX_RATE_DEACTIVATED", "TaxRate", id, null, null, tax.getBranchId());
        return PricingRuleResponse.from(tax);
    }

    // ---------- discount policy ----------

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> listDiscountPolicies() {
        return discountPolicies.findAllByOrderByCodeAsc().stream()
                .map(PricingRuleResponse::from).toList();
    }

    @Transactional
    public PricingRuleResponse createDiscountPolicy(DiscountPolicyRequest request) {
        if (discountPolicies.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Policy code already exists: " + request.code());
        }
        validateWindow(request.effectiveFrom(), request.effectiveTo());
        if (request.maxPercentageWithoutApproval()
                .compareTo(request.maxPercentageWithApproval()) > 0) {
            throw new ValidationException(
                    "The unapproved limit cannot exceed the approved limit");
        }
        if (!request.appliesToMakingCharge() && !request.appliesToMetalValue()) {
            throw new ValidationException(
                    "A discount policy must apply to the making charge, the metal value, or both");
        }

        DiscountPolicy policy = new DiscountPolicy();
        policy.setCode(request.code().trim().toUpperCase());
        policy.setName(request.name().trim());
        policy.setBranchId(request.branchId());
        policy.setMaxPercentageWithoutApproval(request.maxPercentageWithoutApproval());
        policy.setMaxPercentageWithApproval(request.maxPercentageWithApproval());
        policy.setAppliesToMakingCharge(request.appliesToMakingCharge());
        policy.setAppliesToMetalValue(request.appliesToMetalValue());
        policy.setEffectiveFrom(request.effectiveFrom());
        policy.setEffectiveTo(request.effectiveTo());

        DiscountPolicy saved = discountPolicies.save(policy);
        auditService.record("DISCOUNT_POLICY_CREATED", "DiscountPolicy", saved.getId(), null,
                PricingRuleResponse.from(saved), saved.getBranchId());
        return PricingRuleResponse.from(saved);
    }

    private void validateWindow(java.time.LocalDate from, java.time.LocalDate to) {
        if (to != null && to.isBefore(from)) {
            throw new ValidationException("effectiveTo cannot be before effectiveFrom");
        }
    }
}
