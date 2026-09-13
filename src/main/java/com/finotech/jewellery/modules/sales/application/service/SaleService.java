package com.finotech.jewellery.modules.sales.application.service;

import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.inventory.application.InventoryOperations;
import com.finotech.jewellery.modules.loyalty.application.LoyaltySettlement;
import com.finotech.jewellery.modules.pricing.application.PricingCalculator;
import com.finotech.jewellery.modules.pricing.domain.enums.DiscountType;
import com.finotech.jewellery.modules.sales.api.request.CreateSaleRequest;
import com.finotech.jewellery.modules.sales.api.request.SaleReturnRequest;
import com.finotech.jewellery.modules.sales.api.response.SaleResponse;
import com.finotech.jewellery.modules.sales.application.SaleSettlement;
import com.finotech.jewellery.modules.sales.application.SalesHistory;
import com.finotech.jewellery.modules.sales.domain.entity.DiscountRequest;
import com.finotech.jewellery.modules.sales.domain.entity.Sale;
import com.finotech.jewellery.modules.sales.domain.entity.SaleLine;
import com.finotech.jewellery.modules.sales.domain.enums.SaleStatus;
import com.finotech.jewellery.modules.sales.infrastructure.repository.SaleRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Sales and POS.
 *
 * <p>The flow is: open a sale (items are reserved), take payment, and on full
 * settlement the sale is confirmed — which is the single moment items are
 * marked SOLD. Until then nothing has left stock, so an abandoned sale can be
 * cancelled and the items released.
 *
 * <p>Sales never writes item rows: every stock change goes through
 * {@link InventoryOperations} (dependency rule 3). Prices come from the pricing
 * engine and are frozen onto the sale lines, so an invoice can be reproduced
 * exactly even after rates change.
 */
@Service
@RequiredArgsConstructor
public class SaleService implements SaleSettlement, SalesHistory {

    /** How long items are held while a sale awaits payment. */
    private static final int PAYMENT_HOLD_HOURS = 4;

    private final SaleRepository saleRepository;
    private final QuotationService quotationService;
    private final PricingCalculator pricingCalculator;
    private final InventoryOperations inventory;
    private final CustomerDirectory customerDirectory;
    private final LoyaltySettlement loyaltySettlement;
    private final DiscountRequestService discountRequestService;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> search(SaleStatus status, UUID customerId, UUID branchId,
                                             LocalDate from, LocalDate to, Pageable pageable) {
        return PageResponse.of(saleRepository.search(status, customerId, branchId, from, to, pageable),
                SaleResponse::from);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        return SaleResponse.from(requireSale(id));
    }

    // ---------- opening a sale ----------

    /**
     * Opens a sale and holds its items.
     *
     * @param idempotencyKey optional; a replayed key returns the sale already
     *                       opened instead of reserving the items twice
     */
    @Transactional
    public SaleResponse create(CreateSaleRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            var existing = saleRepository.findByExternalReference(idempotencyKey);
            if (existing.isPresent()) {
                return SaleResponse.from(existing.get());
            }
        }

        CustomerDirectory.CustomerView customer =
                customerDirectory.requireTransactableCustomer(request.customerId());
        SecurityUtils.requireBranchAccess(request.branchId());

        Set<UUID> seen = new HashSet<>();
        for (CreateSaleRequest.Line line : request.lines()) {
            if (!seen.add(line.jewelleryItemId())) {
                throw new ValidationException(
                        "The same item appears twice on this sale: " + line.jewelleryItemId());
            }
        }

        // A manager's approval, raised beforehand, stands in for DISCOUNT_APPROVE
        // on this one sale — but only up to what was actually approved.
        boolean holdsApproval = hasDiscountApproval();
        DiscountRequest approvedRequest = request.discountRequestId() == null ? null
                : discountRequestService.requireUsableFor(request.discountRequestId(),
                        request.branchId(), customer.id(), seen);

        Sale sale = new Sale();
        sale.setSaleNumber(CodeGenerator.reference("SL"));
        sale.setCustomerId(customer.id());
        sale.setBranchId(request.branchId());
        sale.setLocationId(request.locationId());
        sale.setQuotationId(request.quotationId());
        sale.setSalespersonId(request.salespersonId());
        sale.setSaleDate(LocalDate.now());
        sale.setNotes(request.notes());
        sale.setStatus(SaleStatus.PENDING_PAYMENT);
        sale.setExchangeCredit(MoneyUtils.money(MoneyUtils.nullSafe(request.exchangeCredit())));
        sale.setExternalReference(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);

        boolean anyDiscountApproved = false;
        BigDecimal manualDiscountUnderRequest = BigDecimal.ZERO;
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (CreateSaleRequest.Line requested : request.lines()) {
            // Reserving first takes the row lock, so a second cashier adding the
            // same item to another sale is rejected here rather than at payment.
            inventory.reserve(requested.jewelleryItemId(), customer.id(), PAYMENT_HOLD_HOURS);

            boolean coveredByRequest = approvedRequest != null
                    && (approvedRequest.getJewelleryItemId() == null
                        || approvedRequest.getJewelleryItemId().equals(requested.jewelleryItemId()));
            boolean discountApproved = holdsApproval || coveredByRequest;
            PricingCalculator.PriceBreakdown price = pricingCalculator.calculate(
                    new PricingCalculator.PriceRequest(
                            requested.jewelleryItemId(), customer.id(), request.branchId(),
                            requested.discountType() == null ? null : requested.discountType().name(),
                            requested.discountValue(), discountApproved));

            if (price.discountRequiresApproval()) {
                anyDiscountApproved = true;
                if (!holdsApproval && coveredByRequest) {
                    assertWithinApprovedDiscount(approvedRequest, requested);
                    manualDiscountUnderRequest = manualDiscountUnderRequest
                            .add(MoneyUtils.nullSafe(price.manualDiscountAmount()));
                }
            }

            SaleLine line = new SaleLine();
            line.setJewelleryItemId(requested.jewelleryItemId());
            line.setItemCode(price.itemCode());
            line.setMetalRateId(price.metalRateId());
            line.setMetalRatePerUnit(price.metalRatePerUnit());
            line.setNetMetalWeight(price.netMetalWeight());
            line.setMetalValue(price.metalValue());
            line.setWastageValue(price.wastageValue());
            line.setMakingCharge(price.makingCharge());
            line.setStoneValue(price.stoneValue());
            line.setTierDiscountAmount(price.tierDiscountAmount());
            line.setLoyaltyTierCode(price.loyaltyTierCode());
            line.setDiscountAmount(price.discountAmount());
            line.setTaxAmount(price.taxTotal());
            line.setLineTotal(price.finalPrice());
            sale.addLine(line);

            subTotal = subTotal.add(price.subTotal());
            discountTotal = discountTotal.add(price.discountAmount());
            taxTotal = taxTotal.add(price.taxTotal());
            total = total.add(price.finalPrice());
            sale.setCurrency(price.currency());
        }

        sale.setSubTotal(MoneyUtils.money(subTotal));
        sale.setDiscountTotal(MoneyUtils.money(discountTotal));
        sale.setTaxTotal(MoneyUtils.money(taxTotal));
        sale.setTotalAmount(MoneyUtils.money(total));
        if (approvedRequest != null && !approvedRequest.isPercentage()
                && manualDiscountUnderRequest.compareTo(approvedRequest.getRequestedAmount()) > 0) {
            throw new ValidationException("Discounts on this sale total "
                    + MoneyUtils.money(manualDiscountUnderRequest) + ", which exceeds the approved "
                    + approvedRequest.getRequestedAmount());
        }
        if (anyDiscountApproved) {
            sale.setDiscountApprovedBy(!holdsApproval && approvedRequest != null
                    ? approvedRequest.getDecidedBy()
                    : SecurityUtils.currentUsername().orElse("system"));
        }

        if (sale.getExchangeCredit().compareTo(sale.getTotalAmount()) > 0) {
            throw new ValidationException("Exchange credit cannot exceed the sale total");
        }

        Sale saved = saleRepository.saveAndFlush(sale);

        if (approvedRequest != null) {
            discountRequestService.consume(approvedRequest.getId(), saved.getId());
        }

        // Points are spent only once the price is settled, so the customer is
        // never charged points against a sale that failed to open.
        if (request.redeemPoints() != null && request.redeemPoints() > 0) {
            applyRedemption(saved, request.redeemPoints());
        }

        if (request.quotationId() != null) {
            quotationService.markConverted(request.quotationId(), saved.getId());
        }

        // Exchange credit and points can cover the price entirely. Nothing will
        // ever be paid, so the sale is settled here rather than waiting for a
        // payment that is not coming.
        if (saved.amountPayable().signum() <= 0) {
            confirm(saved);
        }

        auditService.record("SALE_CREATED", "Sale", saved.getId(), null,
                Map.of("total", saved.getTotalAmount(), "items", saved.getLines().size(),
                        "pointsRedeemed", saved.getLoyaltyPointsRedeemed(),
                        "customerId", String.valueOf(customer.id())),
                saved.getBranchId());
        return SaleResponse.from(saved);
    }

    /**
     * Abandons an unpaid sale and releases the held items back to stock.
     */
    @Transactional
    public SaleResponse cancel(UUID id, String reason) {
        Sale sale = requireSale(id);
        if (!sale.getStatus().isOpen()) {
            throw new ConflictException("Sale " + sale.getSaleNumber() + " is " + sale.getStatus()
                    + " and cannot be cancelled; raise a return instead");
        }
        if (sale.getPaidAmount().signum() > 0) {
            throw new ConflictException(
                    "This sale has payments against it; refund them before cancelling");
        }

        for (SaleLine line : sale.getLines()) {
            inventory.releaseReservation(line.getJewelleryItemId());
        }

        // Points spent on an abandoned sale go back to the customer.
        long returned = loyaltySettlement.refundRedemptionForSale(sale.getId());
        if (returned > 0) {
            sale.setLoyaltyPointsRedeemed(0);
            sale.setLoyaltyRedemptionValue(BigDecimal.ZERO);
        }

        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancellationReason(reason);

        auditService.record("SALE_CANCELLED", "Sale", id, null,
                Map.of("reason", String.valueOf(reason)), sale.getBranchId());
        return SaleResponse.from(sale);
    }

    /**
     * Marks a confirmed sale as handed over to the customer.
     */
    @Transactional
    public SaleResponse deliver(UUID id) {
        Sale sale = requireSale(id);
        if (sale.getStatus() != SaleStatus.CONFIRMED) {
            throw new ConflictException("Only a confirmed sale can be delivered");
        }
        sale.setStatus(SaleStatus.DELIVERED);
        sale.setDeliveredAt(Instant.now());
        auditService.record("SALE_DELIVERED", "Sale", id, null, null, sale.getBranchId());
        return SaleResponse.from(sale);
    }

    /**
     * Returns one or more sold items to stock. The money side is handled by
     * issuing a refund through the payment module.
     */
    @Transactional
    public SaleResponse returnItems(UUID id, SaleReturnRequest request) {
        Sale sale = requireSale(id);
        if (!sale.getStatus().isSettled()) {
            throw new ConflictException("Only a settled sale can have items returned");
        }

        Set<UUID> toReturn = new HashSet<>(request.jewelleryItemIds());
        BigDecimal returnedValue = BigDecimal.ZERO;

        for (SaleLine line : sale.getLines()) {
            if (!toReturn.remove(line.getJewelleryItemId())) {
                continue;
            }
            if (line.isReturned()) {
                throw new ConflictException(
                        "Item " + line.getItemCode() + " has already been returned");
            }
            inventory.markReturned(line.getJewelleryItemId(), sale.getId(),
                    request.returnToLocationId(), request.reason());
            line.setReturned(true);
            line.setReturnReason(request.reason());
            returnedValue = returnedValue.add(line.getLineTotal());
        }
        if (!toReturn.isEmpty()) {
            throw new ValidationException("These items are not on this sale: " + toReturn);
        }

        boolean allReturned = sale.getLines().stream().allMatch(SaleLine::isReturned);
        sale.setStatus(allReturned ? SaleStatus.RETURNED : SaleStatus.PARTIALLY_RETURNED);

        auditService.record("SALE_ITEMS_RETURNED", "Sale", id, null,
                Map.of("items", request.jewelleryItemIds().size(),
                        "value", MoneyUtils.money(returnedValue),
                        "reason", request.reason()),
                sale.getBranchId());
        return SaleResponse.from(sale);
    }

    /**
     * Spends loyalty points against a sale and reduces what the customer pays.
     *
     * <p>Capped at the amount still payable: points are worth money, and letting
     * a redemption exceed the price would hand out value the sale never carried.
     */
    private void applyRedemption(Sale sale, long requestedPoints) {
        BigDecimal payable = sale.getTotalAmount().subtract(sale.getExchangeCredit());
        if (payable.signum() <= 0) {
            throw new ValidationException(
                    "There is nothing left to pay, so points cannot be redeemed on this sale");
        }

        LoyaltySettlement.Redemption redemption = loyaltySettlement.redeemForSale(
                sale.getCustomerId(), requestedPoints, sale.getId(), sale.getBranchId());

        if (redemption.value().compareTo(payable) > 0) {
            throw new ValidationException("Redeeming " + requestedPoints
                    + " points is worth " + redemption.value()
                    + ", which exceeds the amount payable of " + payable);
        }

        sale.setLoyaltyPointsRedeemed(redemption.points());
        sale.setLoyaltyRedemptionValue(MoneyUtils.money(redemption.value()));
    }

    // ---------- settlement seam used by Payment ----------

    @Override
    @Transactional(readOnly = true)
    public SaleBalance balanceOf(UUID saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> NotFoundException.of("Sale", saleId));
        return new SaleBalance(sale.getId(), sale.getCustomerId(), sale.getBranchId(),
                sale.getCurrency(), sale.amountPayable(), sale.getPaidAmount(),
                sale.outstandingAmount(), sale.getStatus().name(),
                sale.getStatus().isOpen() || sale.getStatus().isSettled());
    }

    /**
     * Applies money to a sale and confirms it once fully settled.
     *
     * <p>The sale row is locked for update: two payments arriving at once would
     * otherwise both read the same stale paid total and over-settle the sale.
     */
    @Override
    @Transactional
    public void applyPayment(UUID saleId, BigDecimal amount) {
        Sale sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> NotFoundException.of("Sale", saleId));

        if (amount.signum() > 0 && !sale.getStatus().isOpen()) {
            throw new ConflictException("Sale " + sale.getSaleNumber() + " is " + sale.getStatus()
                    + " and no longer accepts payment");
        }

        BigDecimal newPaid = sale.getPaidAmount().add(amount);
        if (newPaid.signum() < 0) {
            throw new ValidationException("Refunds exceed the amount paid on this sale");
        }
        if (amount.signum() > 0 && newPaid.compareTo(sale.amountPayable()) > 0) {
            throw new ValidationException("Payment exceeds the outstanding balance of "
                    + sale.outstandingAmount());
        }
        sale.setPaidAmount(MoneyUtils.money(newPaid));

        if (amount.signum() < 0) {
            sale.setRefundedAmount(MoneyUtils.money(sale.getRefundedAmount().add(amount.negate())));
        }

        // Full settlement is the single point at which stock is released.
        if (sale.getStatus() == SaleStatus.PENDING_PAYMENT && sale.isFullyPaid()) {
            confirm(sale);
        }
    }

    /**
     * Confirms the sale: allocates an invoice number and marks every item SOLD
     * through the inventory module.
     */
    private void confirm(Sale sale) {
        for (SaleLine line : sale.getLines()) {
            inventory.markSold(line.getJewelleryItemId(), sale.getCustomerId(), sale.getId(),
                    line.getLineTotal());
        }
        sale.setStatus(SaleStatus.CONFIRMED);
        sale.setConfirmedAt(Instant.now());
        sale.setInvoiceNumber(allocateInvoiceNumber());

        auditService.record("SALE_CONFIRMED", "Sale", sale.getId(), null,
                Map.of("invoiceNumber", sale.getInvoiceNumber(),
                        "total", sale.getTotalAmount(),
                        "items", sale.getLines().size()),
                sale.getBranchId());

        // Loyalty, finance and notification all react to this rather than being
        // called from here (section 13).
        events.publish(new DomainEvents.SaleCompleted(sale.getId(), sale.getCustomerId(),
                sale.getBranchId(), sale.getInvoiceNumber(), sale.getTotalAmount(),
                sale.getLines().size()));
    }

    // ---------- purchase history seam ----------

    @Override
    @Transactional(readOnly = true)
    public PurchaseSummary summaryFor(UUID customerId) {
        return summariesFor(List.of(customerId))
                .getOrDefault(customerId, PurchaseSummary.empty(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PurchaseSummary> summariesFor(java.util.Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PurchaseSummary> summaries = new java.util.HashMap<>();
        for (Object[] row : saleRepository.purchaseSummaries(customerIds)) {
            UUID customerId = (UUID) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal total = (BigDecimal) row[2];
            BigDecimal average = count == 0
                    ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(count), MoneyUtils.MONEY_SCALE,
                            java.math.RoundingMode.HALF_UP);
            summaries.put(customerId, new PurchaseSummary(customerId, count,
                    MoneyUtils.money(total), average, (LocalDate) row[3], (LocalDate) row[4]));
        }
        return summaries;
    }

    // ---------- helpers ----------

    private Sale requireSale(UUID id) {
        return saleRepository.findWithLinesById(id)
                .orElseThrow(() -> NotFoundException.of("Sale", id));
    }

    /**
     * A percentage approval caps each covered line at that percentage, on the
     * same basis the pricing policy uses; an amount approval is checked as a
     * total once every line is priced.
     */
    private void assertWithinApprovedDiscount(DiscountRequest approval,
                                              CreateSaleRequest.Line line) {
        if (!approval.isPercentage()) {
            return;
        }
        if (line.discountValue() == null || line.discountValue().signum() <= 0) {
            return;
        }
        if (line.discountType() == DiscountType.AMOUNT) {
            throw new ValidationException("Discount request " + approval.getId()
                    + " approves a percentage; express the discount on item "
                    + line.jewelleryItemId() + " as a percentage");
        }
        if (line.discountValue().compareTo(approval.getRequestedPercentage()) > 0) {
            throw new ValidationException("Discount of " + line.discountValue() + "% on item "
                    + line.jewelleryItemId() + " exceeds the approved "
                    + approval.getRequestedPercentage() + "%");
        }
    }

    private boolean hasDiscountApproval() {
        return SecurityUtils.currentUser()
                .map(user -> user.superAdmin() || user.permissions().contains("DISCOUNT_APPROVE"))
                .orElse(false);
    }

    private String allocateInvoiceNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = CodeGenerator.reference("INV");
            if (!saleRepository.existsByInvoiceNumber(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("Could not allocate an invoice number; retry the request");
    }

    /** Exposed for the daily closing report. */
    @Transactional(readOnly = true)
    public List<Object> dailyTotals(UUID branchId, LocalDate date) {
        return List.of(saleRepository.totalSalesFor(branchId, date),
                saleRepository.countByBranchIdAndSaleDate(branchId, date));
    }
}
