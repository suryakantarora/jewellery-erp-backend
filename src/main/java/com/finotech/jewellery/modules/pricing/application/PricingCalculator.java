package com.finotech.jewellery.modules.pricing.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The published pricing contract, matching {@code calculatePrice(item, customer,
 * branch)} from section 12. Sales calls this and stores the returned breakdown;
 * it never recomputes a price itself, so an invoice always shows exactly what
 * the engine decided.
 */
public interface PricingCalculator {

    PriceBreakdown calculate(PriceRequest request);

    /**
     * @param jewelleryItemId the item to price
     * @param customerId      optional; reserved for customer-specific pricing
     * @param branchId        branch whose rates and rules apply
     * @param discountType    optional PERCENTAGE or AMOUNT
     * @param discountValue   optional discount amount or percentage
     * @param discountApproved whether an approver has authorised a discount
     *                         beyond the branch's own limit
     * @param applyTierDiscount whether the customer's loyalty tier entitlement
     *                          should be applied; a quotation may want the list
     *                          price without it
     */
    record PriceRequest(UUID jewelleryItemId,
                        UUID customerId,
                        UUID branchId,
                        String discountType,
                        BigDecimal discountValue,
                        boolean discountApproved,
                        boolean applyTierDiscount) {

        /** Convenience for callers that always want the tier entitlement applied. */
        public PriceRequest(UUID jewelleryItemId, UUID customerId, UUID branchId,
                            String discountType, BigDecimal discountValue,
                            boolean discountApproved) {
            this(jewelleryItemId, customerId, branchId, discountType, discountValue,
                    discountApproved, true);
        }
    }

    /**
     * A fully itemised price. Every component is returned so an invoice can show
     * the customer how the number was reached, and so the figure can be audited
     * later even after rates change.
     */
    record PriceBreakdown(UUID jewelleryItemId,
                          String itemCode,
                          UUID metalRateId,
                          BigDecimal metalRatePerUnit,
                          BigDecimal netMetalWeight,
                          BigDecimal metalValue,
                          BigDecimal wastagePercentage,
                          BigDecimal wastageWeight,
                          BigDecimal wastageValue,
                          String makingChargeRuleCode,
                          String makingChargeType,
                          BigDecimal makingChargeValue,
                          BigDecimal makingCharge,
                          BigDecimal stoneValue,
                          BigDecimal subTotal,
                          List<TaxLine> taxes,
                          BigDecimal taxTotal,
                          /** Automatic entitlement from the customer's loyalty tier. */
                          String loyaltyTierCode,
                          BigDecimal tierDiscountPercentage,
                          BigDecimal tierDiscountAmount,
                          /** Discount requested by staff, subject to the branch policy. */
                          BigDecimal manualDiscountAmount,
                          /** tierDiscountAmount + manualDiscountAmount. */
                          BigDecimal discountAmount,
                          BigDecimal finalPrice,
                          String currency,
                          boolean discountRequiresApproval) {
    }

    record TaxLine(String code, String name, BigDecimal percentage, BigDecimal amount,
                   boolean inclusive) {
    }
}
