package com.finotech.jewellery.modules.exchange.application.service;

import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.exchange.api.request.ExchangeRequests;
import com.finotech.jewellery.modules.exchange.api.response.ExchangeResponse;
import com.finotech.jewellery.modules.exchange.domain.entity.ExchangeIntake;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeStatus;
import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeType;
import com.finotech.jewellery.modules.exchange.infrastructure.repository.ExchangeIntakeRepository;
import com.finotech.jewellery.modules.metal.application.MetalRateProvider;
import com.finotech.jewellery.modules.metal.domain.enums.RateType;
import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchange and buyback of old jewellery.
 *
 * <p>The workflow from section 15 is enforced step by step — weigh, test purity,
 * value, approve, complete — and each step is attributed to the user who
 * performed it. The valuation is derived from recorded measurements rather than
 * typed in, so the money paid out always traces back to a weight and a test.
 *
 * <p>Buying uses the {@code BUYING} rate, which is normally below the selling
 * rate; this module never touches selling prices.
 */
@Service
@RequiredArgsConstructor
public class ExchangeService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ExchangeIntakeRepository intakeRepository;
    private final MetalRateProvider metalRates;
    private final CustomerDirectory customerDirectory;
    private final OrganizationDirectory organizationDirectory;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<ExchangeResponse> search(ExchangeStatus status, ExchangeType type,
                                                 UUID customerId, UUID branchId,
                                                 Pageable pageable) {
        return PageResponse.of(intakeRepository.search(status, type, customerId, branchId, pageable),
                ExchangeResponse::from);
    }

    @Transactional(readOnly = true)
    public ExchangeResponse get(UUID id) {
        return ExchangeResponse.from(requireIntake(id));
    }

    /** Step 1: the piece is taken in and described. */
    @Transactional
    public ExchangeResponse receive(ExchangeRequests.ReceiveRequest request) {
        CustomerDirectory.CustomerView customer =
                customerDirectory.requireTransactableCustomer(request.customerId());
        SecurityUtils.requireBranchAccess(request.branchId());

        ExchangeIntake intake = new ExchangeIntake();
        intake.setReferenceNumber(CodeGenerator.reference(
                request.exchangeType() == ExchangeType.BUYBACK ? "BB" : "EX"));
        intake.setExchangeType(request.exchangeType());
        intake.setCustomerId(customer.id());
        intake.setBranchId(request.branchId());
        intake.setLocationId(request.locationId());
        intake.setMetalId(request.metalId());
        intake.setDeclaredPurityId(request.declaredPurityId());
        intake.setOriginalItemId(request.originalItemId());
        intake.setDescription(request.description().trim());
        intake.setItemCount(Math.max(request.itemCount(), 1));
        intake.setReceivedDate(LocalDate.now());
        intake.setNotes(request.notes());
        intake.setStatus(ExchangeStatus.RECEIVED);

        ExchangeIntake saved = intakeRepository.save(intake);
        auditService.record("EXCHANGE_RECEIVED", "ExchangeIntake", saved.getId(), null,
                Map.of("type", saved.getExchangeType(), "customerId", String.valueOf(customer.id())),
                saved.getBranchId());
        return ExchangeResponse.from(saved);
    }

    /** Step 2: gross and stone weights are recorded; net weight is derived. */
    @Transactional
    public ExchangeResponse weigh(UUID id, ExchangeRequests.WeighRequest request) {
        ExchangeIntake intake = requireIntake(id);
        intake.transitionTo(ExchangeStatus.WEIGHED);

        BigDecimal gross = MoneyUtils.weight(request.grossWeight());
        BigDecimal stones = MoneyUtils.weight(MoneyUtils.nullSafe(request.stoneWeight()));
        BigDecimal net = gross.subtract(stones);
        if (net.signum() <= 0) {
            throw new ValidationException(
                    "Stone weight cannot equal or exceed the gross weight");
        }

        intake.setGrossWeight(gross);
        intake.setStoneWeight(stones);
        intake.setNetWeight(net);
        intake.setWeighedBy(SecurityUtils.currentUsername().orElse("system"));
        intake.setWeighedAt(Instant.now());

        auditService.record("EXCHANGE_WEIGHED", "ExchangeIntake", id, null,
                Map.of("grossWeight", gross, "netWeight", net), intake.getBranchId());
        return ExchangeResponse.from(intake);
    }

    /**
     * Step 3: the tested purity is recorded. This is what the valuation uses,
     * not whatever the customer believes the piece to be.
     */
    @Transactional
    public ExchangeResponse testPurity(UUID id, ExchangeRequests.PurityTestRequest request) {
        ExchangeIntake intake = requireIntake(id);
        intake.transitionTo(ExchangeStatus.PURITY_TESTED);

        BigDecimal fineness = metalRates.fineness(request.testedPurityId());
        intake.setTestedPurityId(request.testedPurityId());
        intake.setTestedFineness(fineness);
        intake.setTestMethod(request.testMethod());
        intake.setTestedBy(SecurityUtils.currentUsername().orElse("system"));
        intake.setTestedAt(Instant.now());

        auditService.record("EXCHANGE_PURITY_TESTED", "ExchangeIntake", id,
                Map.of("declaredPurityId", String.valueOf(intake.getDeclaredPurityId())),
                Map.of("testedPurityId", request.testedPurityId(), "fineness", fineness),
                intake.getBranchId());
        return ExchangeResponse.from(intake);
    }

    /**
     * Step 4: values the metal at the buying rate in force.
     *
     * <pre>
     *   pure weight     = net weight x tested fineness
     *   gross valuation = pure weight x buying rate
     *   net valuation   = gross valuation - deduction
     * </pre>
     */
    @Transactional
    public ExchangeResponse value(UUID id, ExchangeRequests.ValuationRequest request) {
        ExchangeIntake intake = requireIntake(id);
        intake.transitionTo(ExchangeStatus.VALUED);

        if (intake.getNetWeight() == null || intake.getTestedPurityId() == null) {
            throw new ValidationException("Weigh and purity-test the piece before valuing it");
        }

        MetalRateProvider.RateView rate = metalRates.requireEffectiveRate(
                intake.getMetalId(), intake.getTestedPurityId(), RateType.BUYING,
                LocalDate.now(), intake.getBranchId());

        // The rate is published per unit weight of that purity, so it applies to
        // the net weight directly; pure weight is recorded for traceability.
        BigDecimal pureWeight = MoneyUtils.weight(
                intake.getNetWeight().multiply(intake.getTestedFineness()));
        BigDecimal grossValuation = intake.getNetWeight().multiply(rate.ratePerUnit());

        BigDecimal deductionPercentage = MoneyUtils.nullSafe(request.deductionPercentage());
        BigDecimal deduction = grossValuation.multiply(deductionPercentage)
                .divide(HUNDRED, MoneyUtils.MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal netValuation = grossValuation.subtract(deduction);

        intake.setRateId(rate.rateId());
        intake.setRatePerUnit(MoneyUtils.money(rate.ratePerUnit()));
        intake.setPureWeight(pureWeight);
        intake.setGrossValuation(MoneyUtils.money(grossValuation));
        intake.setDeductionPercentage(deductionPercentage);
        intake.setDeductionAmount(MoneyUtils.money(deduction));
        intake.setNetValuation(MoneyUtils.money(netValuation));
        intake.setCurrency(rate.currency());
        intake.setValuedBy(SecurityUtils.currentUsername().orElse("system"));
        intake.setValuedAt(Instant.now());
        if (request.notes() != null) {
            intake.setNotes(request.notes());
        }

        // Paying out money always goes past a second pair of eyes.
        intake.transitionTo(ExchangeStatus.PENDING_APPROVAL);

        auditService.record("EXCHANGE_VALUED", "ExchangeIntake", id, null,
                Map.of("ratePerUnit", intake.getRatePerUnit(),
                        "grossValuation", intake.getGrossValuation(),
                        "netValuation", intake.getNetValuation()),
                intake.getBranchId());
        return ExchangeResponse.from(intake);
    }

    /** Step 5: an approver accepts the valuation. */
    @Transactional
    public ExchangeResponse approve(UUID id) {
        ExchangeIntake intake = requireIntake(id);
        intake.transitionTo(ExchangeStatus.APPROVED);

        String approver = SecurityUtils.currentUsername().orElse("system");
        if (approver.equals(intake.getValuedBy())) {
            throw new ValidationException(
                    "A valuation must be approved by someone other than the valuer");
        }
        intake.setApprovedBy(approver);
        intake.setApprovedAt(Instant.now());

        auditService.record("EXCHANGE_APPROVED", "ExchangeIntake", id, null,
                Map.of("netValuation", intake.getNetValuation(), "approvedBy", approver),
                intake.getBranchId());
        return ExchangeResponse.from(intake);
    }

    @Transactional
    public ExchangeResponse reject(UUID id, String reason) {
        ExchangeIntake intake = requireIntake(id);
        intake.transitionTo(ExchangeStatus.REJECTED);
        intake.setRejectionReason(reason);
        auditService.record("EXCHANGE_REJECTED", "ExchangeIntake", id, null,
                Map.of("reason", String.valueOf(reason)), intake.getBranchId());
        return ExchangeResponse.from(intake);
    }

    /**
     * Step 6: settles the intake. An exchange records the sale its credit was
     * applied to; a buyback stands alone and is paid out.
     */
    @Transactional
    public ExchangeResponse complete(UUID id, ExchangeRequests.CompleteRequest request) {
        ExchangeIntake intake = requireIntake(id);
        intake.transitionTo(ExchangeStatus.COMPLETED);

        if (intake.getExchangeType() == ExchangeType.EXCHANGE && request.appliedSaleId() == null) {
            throw new ValidationException(
                    "An exchange must name the sale its credit was applied to");
        }
        if (request.scrapLocationId() != null) {
            OrganizationDirectory.LocationView location =
                    organizationDirectory.requireLocation(request.scrapLocationId());
            if (!location.canHoldStock()) {
                throw new ValidationException("Scrap location cannot hold stock");
            }
        }

        intake.setAppliedSaleId(request.appliedSaleId());
        intake.setCompletedAt(Instant.now());
        if (request.notes() != null) {
            intake.setNotes(request.notes());
        }

        auditService.record("EXCHANGE_COMPLETED", "ExchangeIntake", id, null,
                Map.of("netValuation", intake.getNetValuation(),
                        "appliedSaleId", String.valueOf(request.appliedSaleId())),
                intake.getBranchId());

        events.publish(new DomainEvents.ExchangeCompleted(intake.getId(), intake.getCustomerId(),
                intake.getBranchId(), intake.getReferenceNumber(), intake.getNetValuation()));
        return ExchangeResponse.from(intake);
    }

    /** The customer declines the offer and takes the piece back. */
    @Transactional
    public ExchangeResponse returnToCustomer(UUID id, String reason) {
        ExchangeIntake intake = requireIntake(id);
        intake.transitionTo(ExchangeStatus.RETURNED_TO_CUSTOMER);
        intake.setNotes(reason);
        auditService.record("EXCHANGE_RETURNED_TO_CUSTOMER", "ExchangeIntake", id, null,
                Map.of("reason", String.valueOf(reason)), intake.getBranchId());
        return ExchangeResponse.from(intake);
    }

    private ExchangeIntake requireIntake(UUID id) {
        return intakeRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("ExchangeIntake", id));
    }
}
