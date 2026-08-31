package com.finotech.jewellery.modules.loyalty.application.service;

import com.finotech.jewellery.modules.customer.application.CustomerDirectory;
import com.finotech.jewellery.modules.loyalty.api.request.LoyaltyRequests;
import com.finotech.jewellery.modules.loyalty.api.response.LoyaltyResponses.AccountResponse;
import com.finotech.jewellery.modules.loyalty.api.response.LoyaltyResponses.TransactionResponse;
import com.finotech.jewellery.modules.loyalty.application.LoyaltyDirectory;
import com.finotech.jewellery.modules.loyalty.application.LoyaltySettlement;
import com.finotech.jewellery.modules.loyalty.application.LoyaltyTierBenefits;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyAccount;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyProgram;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyTier;
import com.finotech.jewellery.modules.loyalty.domain.entity.LoyaltyTransaction;
import com.finotech.jewellery.modules.loyalty.domain.enums.LoyaltyTransactionType;
import com.finotech.jewellery.modules.loyalty.infrastructure.repository.LoyaltyAccountRepository;
import com.finotech.jewellery.modules.loyalty.infrastructure.repository.LoyaltyProgramRepository;
import com.finotech.jewellery.modules.loyalty.infrastructure.repository.LoyaltyTransactionRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.event.DomainEventPublisher;
import com.finotech.jewellery.shared.event.DomainEvents;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loyalty points, tiers and redemption.
 *
 * <p>The transaction history is the record of truth; the balance on the account
 * is a running total kept for fast lookup. Both move together under a row lock,
 * so two concurrent redemptions cannot spend the same points twice.
 *
 * <p>Awarding is idempotent per sale: the reference is checked before points are
 * granted, so a replayed event cannot double-credit a customer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyService implements LoyaltyDirectory, LoyaltyTierBenefits, LoyaltySettlement {

    private static final String SALE_REFERENCE = "Sale";

    private final LoyaltyProgramRepository programRepository;
    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final CustomerDirectory customerDirectory;
    private final AuditService auditService;
    private final DomainEventPublisher events;

    // ---------- accounts ----------

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID customerId) {
        LoyaltyAccount account = accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NotFoundException(
                        "Customer " + customerId + " is not enrolled in a loyalty program"));
        return AccountResponse.from(account, redeemableValue(account));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> statement(UUID customerId, Pageable pageable) {
        return PageResponse.of(
                transactionRepository.findAllByCustomerIdOrderByOccurredAtDesc(customerId, pageable),
                TransactionResponse::from);
    }

    /**
     * Enrols a customer, or returns the existing account. Enrolment is
     * deliberately idempotent so a POS can call it without checking first.
     */
    @Transactional
    public AccountResponse enrol(UUID customerId) {
        CustomerDirectory.CustomerView customer =
                customerDirectory.requireTransactableCustomer(customerId);

        return accountRepository.findByCustomerId(customerId)
                .map(existing -> AccountResponse.from(existing, redeemableValue(existing)))
                .orElseGet(() -> {
                    LoyaltyAccount account = createAccount(customer.id(), activeProgram());
                    auditService.record("LOYALTY_ENROLLED", "LoyaltyAccount", account.getId(),
                            null, Map.of("customerId", String.valueOf(customer.id())));
                    return AccountResponse.from(account, redeemableValue(account));
                });
    }

    // ---------- earning ----------

    /**
     * Awards points for a completed sale.
     *
     * <p>Called from the sale event listener rather than from Sales itself, so
     * loyalty can be changed or switched off without touching the sale path.
     *
     * @return the points awarded, or zero if the sale earned none
     */
    @Transactional
    public long awardForSale(UUID customerId, UUID saleId, BigDecimal eligibleAmount,
                             UUID branchId) {
        // Checked here for a clear early exit; a unique index on the EARN
        // reference is the actual guarantee against a concurrent double-award.
        if (transactionRepository.existsByReferenceTypeAndReferenceIdAndTransactionType(
                SALE_REFERENCE, String.valueOf(saleId), LoyaltyTransactionType.EARN)) {
            log.debug("Sale {} has already earned points; skipping", saleId);
            return 0;
        }
        if (eligibleAmount == null || eligibleAmount.signum() <= 0) {
            return 0;
        }

        LoyaltyAccount account = accountRepository.findByCustomerIdForUpdate(customerId)
                .orElseGet(() -> createAccount(customerId, activeProgram()));
        LoyaltyProgram program = account.getProgram();

        BigDecimal multiplier = account.getTier() == null
                ? BigDecimal.ONE
                : account.getTier().getEarnMultiplier();
        long points = eligibleAmount
                .multiply(program.getPointsPerCurrencyUnit())
                .multiply(multiplier)
                .setScale(0, RoundingMode.DOWN)
                .longValue();

        if (points <= 0) {
            return 0;
        }

        account.earn(points);
        applyTier(account);

        LocalDate expiresOn = program.getPointsValidityMonths() == null
                ? null
                : LocalDate.now().plusMonths(program.getPointsValidityMonths());

        record(account, LoyaltyTransactionType.EARN, points, eligibleAmount, SALE_REFERENCE,
                saleId, branchId, expiresOn, "Points earned on sale");

        auditService.record("LOYALTY_POINTS_EARNED", "LoyaltyAccount", account.getId(), null,
                Map.of("saleId", String.valueOf(saleId), "points", points,
                        "balance", account.getPointsBalance()),
                branchId);

        // Finance recognises the liability these points create.
        events.publish(new DomainEvents.LoyaltyPointsEarned(customerId, saleId, branchId, points,
                MoneyUtils.money(BigDecimal.valueOf(points)
                        .multiply(program.getCurrencyValuePerPoint()))));
        return points;
    }

    /** Reverses the points awarded for a sale that was returned. */
    @Transactional
    public long reverseForSale(UUID saleId) {
        List<LoyaltyTransaction> awarded = transactionRepository
                .findAllByReferenceTypeAndReferenceId(SALE_REFERENCE, String.valueOf(saleId))
                .stream()
                .filter(t -> t.getTransactionType() == LoyaltyTransactionType.EARN)
                .toList();
        if (awarded.isEmpty()) {
            return 0;
        }

        long total = 0;
        for (LoyaltyTransaction earn : awarded) {
            LoyaltyAccount account = accountRepository.findByCustomerIdForUpdate(earn.getCustomerId())
                    .orElseThrow(() -> NotFoundException.of("LoyaltyAccount", earn.getCustomerId()));
            account.reverse(earn.getPoints());
            applyTier(account);
            record(account, LoyaltyTransactionType.REVERSAL, -earn.getPoints(), null,
                    SALE_REFERENCE, saleId, earn.getBranchId(), null,
                    "Reversed because the sale was returned");
            total += earn.getPoints();
        }

        auditService.record("LOYALTY_POINTS_REVERSED", "Sale", saleId, null,
                Map.of("points", total));
        return total;
    }

    // ---------- redeeming ----------

    /**
     * Spends points. The account row is locked for the whole operation, which is
     * what stops the same balance being spent twice at two counters.
     */
    @Transactional
    public AccountResponse redeem(LoyaltyRequests.RedeemRequest request) {
        LoyaltyAccount account = accountRepository.findByCustomerIdForUpdate(request.customerId())
                .orElseThrow(() -> new NotFoundException(
                        "Customer " + request.customerId() + " is not enrolled"));
        if (!account.isActive()) {
            throw new ConflictException("This loyalty account is not active");
        }

        Integer minimum = account.getProgram().getMinimumRedeemablePoints();
        if (minimum != null && request.points() < minimum) {
            throw new ValidationException(
                    "At least " + minimum + " points are needed to redeem");
        }

        account.redeem(request.points());
        BigDecimal value = MoneyUtils.money(BigDecimal.valueOf(request.points())
                .multiply(account.getProgram().getCurrencyValuePerPoint()));

        record(account, LoyaltyTransactionType.REDEEM, -request.points(), value,
                request.saleId() == null ? null : SALE_REFERENCE, request.saleId(),
                request.branchId(), null, request.reason());

        auditService.record("LOYALTY_POINTS_REDEEMED", "LoyaltyAccount", account.getId(), null,
                Map.of("points", request.points(), "value", value,
                        "balance", account.getPointsBalance()),
                request.branchId());
        return AccountResponse.from(account, redeemableValue(account));
    }

    /** Manual correction. Restricted and always carries a reason. */
    @Transactional
    public AccountResponse adjust(LoyaltyRequests.AdjustRequest request) {
        LoyaltyAccount account = accountRepository.findByCustomerIdForUpdate(request.customerId())
                .orElseThrow(() -> new NotFoundException(
                        "Customer " + request.customerId() + " is not enrolled"));
        long before = account.getPointsBalance();
        account.adjust(request.points());
        applyTier(account);

        record(account, LoyaltyTransactionType.ADJUSTMENT, request.points(), null, "Manual",
                null, null, null, request.reason());

        auditService.record("LOYALTY_POINTS_ADJUSTED", "LoyaltyAccount", account.getId(),
                Map.of("balance", before),
                Map.of("balance", account.getPointsBalance(), "reason", request.reason()));
        return AccountResponse.from(account, redeemableValue(account));
    }

    /**
     * Expires points that have passed their validity date. Run on a schedule;
     * safe to run repeatedly because each award is only expired once.
     */
    @Transactional
    public int expireDuePoints(int batchSize) {
        List<LoyaltyTransaction> due =
                transactionRepository.findExpiring(LocalDate.now(), PageRequest.ofSize(batchSize));
        int expired = 0;

        for (LoyaltyTransaction earn : due) {
            LoyaltyAccount account = accountRepository
                    .findByCustomerIdForUpdate(earn.getCustomerId()).orElse(null);
            earn.setExpired(true);
            if (account == null || account.getPointsBalance() <= 0) {
                continue;
            }
            long expiring = Math.min(earn.getPoints(), account.getPointsBalance());
            account.expire(expiring);
            record(account, LoyaltyTransactionType.EXPIRY, -expiring, null, "Expiry",
                    earn.getId(), earn.getBranchId(), null,
                    "Points earned on " + earn.getOccurredAt() + " have expired");
            expired++;
        }
        if (expired > 0) {
            log.info("Expired points on {} loyalty award(s)", expired);
        }
        return expired;
    }

    // ---------- cross-module directory ----------

    @Override
    @Transactional(readOnly = true)
    public AccountView findAccount(UUID customerId) {
        return accountRepository.findByCustomerId(customerId)
                .map(a -> new AccountView(a.getId(), a.getCustomerId(),
                        a.getTier() == null ? null : a.getTier().getCode(),
                        a.getTier() == null ? null : a.getTier().getName(),
                        a.getPointsBalance(), a.getLifetimePoints(), redeemableValue(a)))
                .orElse(null);
    }

    // ---------- settlement seam used by Sales ----------

    @Override
    @Transactional
    public Redemption redeemForSale(UUID customerId, long points, UUID saleId, UUID branchId) {
        AccountResponse account = redeem(new LoyaltyRequests.RedeemRequest(
                customerId, points, saleId, branchId, "Redeemed against sale"));
        BigDecimal value = MoneyUtils.money(BigDecimal.valueOf(points)
                .multiply(programOf(customerId).getCurrencyValuePerPoint()));
        return new Redemption(points, value);
    }

    /**
     * Hands back points spent on a sale that did not complete. Recorded as an
     * adjustment rather than by deleting the redemption, so the statement still
     * shows both the spend and its reversal.
     */
    @Override
    @Transactional
    public long refundRedemptionForSale(UUID saleId) {
        List<LoyaltyTransaction> redemptions = transactionRepository
                .findAllByReferenceTypeAndReferenceId(SALE_REFERENCE, String.valueOf(saleId))
                .stream()
                .filter(t -> t.getTransactionType() == LoyaltyTransactionType.REDEEM)
                .toList();
        if (redemptions.isEmpty()) {
            return 0;
        }

        long returned = 0;
        for (LoyaltyTransaction redemption : redemptions) {
            // Points were recorded as a negative movement, so the amount to give
            // back is its magnitude.
            long points = Math.abs(redemption.getPoints());
            LoyaltyAccount account = accountRepository
                    .findByCustomerIdForUpdate(redemption.getCustomerId())
                    .orElseThrow(() -> NotFoundException.of("LoyaltyAccount",
                            redemption.getCustomerId()));
            account.returnRedeemed(points);
            record(account, LoyaltyTransactionType.ADJUSTMENT, points, null, SALE_REFERENCE,
                    saleId, redemption.getBranchId(),
                    null, "Points returned because the sale did not complete");
            returned += points;
        }

        auditService.record("LOYALTY_REDEMPTION_REFUNDED", "Sale", saleId, null,
                Map.of("points", returned));
        return returned;
    }

    @Override
    @Transactional(readOnly = true)
    public TierBenefit benefitFor(UUID customerId) {
        return accountRepository.findByCustomerId(customerId)
                .filter(LoyaltyAccount::isActive)
                .map(LoyaltyAccount::getTier)
                .filter(tier -> tier != null && tier.getDiscountPercentage() != null)
                .map(tier -> new TierBenefit(tier.getCode(), tier.getName(),
                        tier.getDiscountPercentage()))
                .orElse(null);
    }

    // ---------- helpers ----------

    private LoyaltyAccount createAccount(UUID customerId, LoyaltyProgram program) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setCustomerId(customerId);
        account.setProgram(program);
        account.setEnrolledAt(Instant.now());
        account.setTier(program.tierFor(0));
        return accountRepository.saveAndFlush(account);
    }

    /** Promotes the customer if their lifetime points now reach a higher tier. */
    private void applyTier(LoyaltyAccount account) {
        LoyaltyProgram program = programRepository
                .findWithTiersById(account.getProgram().getId())
                .orElse(account.getProgram());
        LoyaltyTier tier = program.tierFor(account.getLifetimePoints());
        if (tier != null && (account.getTier() == null
                || !tier.getId().equals(account.getTier().getId()))) {
            account.setTier(tier);
        }
    }

    private LoyaltyProgram programOf(UUID customerId) {
        return accountRepository.findByCustomerId(customerId)
                .map(LoyaltyAccount::getProgram)
                .orElseGet(this::activeProgram);
    }

    private LoyaltyProgram activeProgram() {
        return programRepository.findActive(LocalDate.now(), PageRequest.ofSize(1)).stream()
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "No active loyalty program is configured"));
    }

    private BigDecimal redeemableValue(LoyaltyAccount account) {
        return MoneyUtils.money(BigDecimal.valueOf(account.getPointsBalance())
                .multiply(account.getProgram().getCurrencyValuePerPoint()));
    }

    private void record(LoyaltyAccount account, LoyaltyTransactionType type, long points,
                        BigDecimal monetaryValue, String referenceType, Object referenceId,
                        UUID branchId, LocalDate expiresOn, String reason) {
        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setAccountId(account.getId());
        transaction.setCustomerId(account.getCustomerId());
        transaction.setTransactionType(type);
        transaction.setPoints(points);
        transaction.setBalanceAfter(account.getPointsBalance());
        transaction.setMonetaryValue(monetaryValue);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId == null ? null : String.valueOf(referenceId));
        transaction.setBranchId(branchId);
        transaction.setExpiresOn(expiresOn);
        transaction.setReason(reason);
        transaction.setOccurredAt(Instant.now());
        transactionRepository.save(transaction);
    }
}
