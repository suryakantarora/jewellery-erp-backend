package com.finotech.jewellery.modules.compliance.application.service;

import com.finotech.jewellery.modules.compliance.api.response.ComplianceReports.AuditSummaryReport;
import com.finotech.jewellery.modules.compliance.api.response.ComplianceReports.DualAuthorisationReport;
import com.finotech.jewellery.modules.compliance.api.response.ComplianceReports.HighValueTransactionReport;
import com.finotech.jewellery.modules.compliance.api.response.ComplianceReports.KycStatusReport;
import com.finotech.jewellery.modules.compliance.domain.ComplianceThresholds;
import com.finotech.jewellery.modules.compliance.infrastructure.repository.ComplianceQueryRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regulatory and control reporting (section 23).
 *
 * <p>Everything here is read-only and derived from what the business modules
 * already record: the audit trail, KYC state and the approvals captured on vault
 * movements, stock counts and buyback valuations. Nothing new is written by the
 * business to make these reports possible, which is what keeps them honest.
 *
 * <p>Running a compliance report is itself audited: who asked what, and when, is
 * part of the control.
 */
@Service
@RequiredArgsConstructor
public class ComplianceReportService {

    /**
     * Actions worth flagging in an audit summary: money moved, a control was
     * overridden, or someone's access changed.
     */
    private static final List<String> SENSITIVE_ACTIONS = List.of(
            "PAYMENT_CAPTURED", "PAYMENT_REFUNDED", "SALE_CONFIRMED", "SALE_CANCELLED",
            "EXCHANGE_APPROVED", "EXCHANGE_COMPLETED",
            "ITEM_STATUS_CHANGED", "MOVEMENT_APPROVED",
            "STOCK_COUNT_APPROVED", "LOYALTY_POINTS_ADJUSTED",
            "MAKING_CHARGE_RULE_CREATED", "TAX_RATE_CREATED", "DISCOUNT_POLICY_CREATED",
            "METAL_RATE_PUBLISHED",
            "USER_CREATED", "USER_UPDATED", "PASSWORD_RESET", "ROLE_CREATED", "ROLE_UPDATED",
            "CUSTOMER_KYC_DECIDED", "SUPPLIER_BANK_ACCOUNT_ADDED");

    private static final int EXCEPTION_LIMIT = 200;
    private static final int SENSITIVE_EVENT_LIMIT = 200;

    private final ComplianceQueryRepository queries;
    private final ComplianceThresholds thresholds;
    private final AuditService auditService;

    /**
     * Sales at or above the reportable amount.
     *
     * <p>Cash is reported separately because cash usually carries a lower
     * threshold than the sale as a whole, and KYC state is shown alongside
     * because that is the first question asked about a large purchase.
     */
    @Transactional(readOnly = true)
    public HighValueTransactionReport highValueTransactions(LocalDate from, LocalDate to,
                                                            UUID branchId, BigDecimal override) {
        BigDecimal threshold = override != null ? override : thresholds.highValueSaleAmount();
        List<ComplianceQueryRepository.HighValueRow> rows =
                queries.highValueSales(from, to, threshold, branchId);

        List<HighValueTransactionReport.HighValueTransaction> transactions = rows.stream()
                .map(r -> new HighValueTransactionReport.HighValueTransaction(
                        r.saleId(), r.saleNumber(), r.invoiceNumber(), r.saleDate(), r.branchId(),
                        r.customerId(), r.customerName(), r.customerCode(), r.kycStatus(),
                        isVerified(r.kycStatus()), r.totalAmount(), r.cashAmount(),
                        r.cashAmount().compareTo(thresholds.highValueCashAmount()) >= 0))
                .toList();

        BigDecimal total = transactions.stream()
                .map(HighValueTransactionReport.HighValueTransaction::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int unverified = (int) transactions.stream()
                .filter(t -> !t.kycVerified()).count();

        auditService.record("COMPLIANCE_REPORT_RUN", "HighValueTransactionReport", null, null,
                Map.of("from", from, "to", to, "threshold", threshold,
                        "matches", transactions.size()),
                branchId);

        return new HighValueTransactionReport(from, to, threshold, transactions.size(), total,
                unverified, transactions);
    }

    /**
     * Customers with a run of purchases just below the threshold — the shape of
     * a single purchase split to stay under a reporting limit.
     *
     * <p>This flags a pattern for a human to look at. It is not an accusation,
     * and the platform takes no action on it.
     */
    @Transactional(readOnly = true)
    public List<ComplianceQueryRepository.StructuringRow> possibleStructuring() {
        LocalDate from = LocalDate.now().minusDays(thresholds.structuringWindowDays());
        List<ComplianceQueryRepository.StructuringRow> rows = queries.possibleStructuring(
                from, thresholds.highValueSaleAmount(), thresholds.structuringCount());

        auditService.record("COMPLIANCE_REPORT_RUN", "StructuringReport", null, null,
                Map.of("windowDays", thresholds.structuringWindowDays(), "matches", rows.size()));
        return rows;
    }

    /** KYC posture across the active customer base, with the exceptions listed. */
    @Transactional(readOnly = true)
    public KycStatusReport kycStatus() {
        LocalDate asOf = LocalDate.now();
        Map<String, Long> counts = queries.kycCounts().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ComplianceQueryRepository.KycCountRow::kycStatus,
                        ComplianceQueryRepository.KycCountRow::total));

        List<ComplianceQueryRepository.KycExceptionRow> exceptionRows =
                queries.kycExceptions(asOf, EXCEPTION_LIMIT);
        List<KycStatusReport.KycException> exceptions = exceptionRows.stream()
                .map(r -> new KycStatusReport.KycException(r.customerId(), r.customerCode(),
                        r.customerName(), r.kycStatus(), r.issue(), r.documentExpiry(),
                        r.lifetimeSpend()))
                .toList();

        long verifiedButExpired = exceptions.stream()
                .filter(e -> "VERIFIED".equals(e.kycStatus()))
                .count();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        auditService.record("COMPLIANCE_REPORT_RUN", "KycStatusReport", null, null,
                Map.of("customers", total, "exceptions", exceptions.size()));

        return new KycStatusReport(asOf, total,
                counts.getOrDefault("VERIFIED", 0L),
                counts.getOrDefault("PENDING", 0L),
                counts.getOrDefault("REJECTED", 0L),
                counts.getOrDefault("NOT_REQUIRED", 0L),
                counts.getOrDefault("EXPIRED", 0L),
                verifiedButExpired, exceptions);
    }

    /**
     * Every action that passed through a dual-authorisation control, and whether
     * it actually received two approvers.
     */
    @Transactional(readOnly = true)
    public DualAuthorisationReport dualAuthorisation(LocalDate from, LocalDate to, UUID branchId) {
        List<ComplianceQueryRepository.ControlRow> rows =
                queries.dualAuthorisationActions(from, to, branchId);

        List<DualAuthorisationReport.ControlledAction> actions = rows.stream()
                .map(r -> new DualAuthorisationReport.ControlledAction(
                        r.controlType(), r.entityId(), r.reference(), r.branchId(),
                        r.firstApprover(), r.firstApprovedAt(), r.secondApprover(),
                        r.secondApprovedAt(), r.status(),
                        r.firstApprover() != null && r.secondApprover() != null,
                        r.note()))
                .toList();

        int fully = (int) actions.stream()
                .filter(DualAuthorisationReport.ControlledAction::fullyAuthorised).count();

        auditService.record("COMPLIANCE_REPORT_RUN", "DualAuthorisationReport", null, null,
                Map.of("from", from, "to", to, "controlled", actions.size()), branchId);

        return new DualAuthorisationReport(from, to, actions.size(), fully,
                actions.size() - fully, actions);
    }

    /** Who did what over a period, with the sensitive actions called out. */
    @Transactional(readOnly = true)
    public AuditSummaryReport auditSummary(Instant from, Instant to) {
        List<AuditSummaryReport.ActionCount> byAction = queries.auditCountsByAction(from, to)
                .stream()
                .map(r -> new AuditSummaryReport.ActionCount(r.action(), r.entityType(), r.count()))
                .toList();

        List<AuditSummaryReport.ActorCount> byUser =
                queries.auditCountsByUser(from, to, SENSITIVE_ACTIONS).stream()
                        .map(r -> new AuditSummaryReport.ActorCount(r.username(), r.count(),
                                r.sensitiveCount()))
                        .toList();

        List<AuditSummaryReport.SensitiveEvent> sensitive =
                queries.sensitiveAuditEvents(from, to, SENSITIVE_ACTIONS, SENSITIVE_EVENT_LIMIT)
                        .stream()
                        .map(r -> new AuditSummaryReport.SensitiveEvent(r.id(), r.username(),
                                r.action(), r.entityType(), r.entityId(), r.branchId(),
                                r.occurredAt()))
                        .toList();

        long totalEvents = byAction.stream()
                .mapToLong(AuditSummaryReport.ActionCount::count).sum();

        return new AuditSummaryReport(from, to, totalEvents, byAction, byUser, sensitive);
    }

    private boolean isVerified(String kycStatus) {
        return "VERIFIED".equals(kycStatus) || "NOT_REQUIRED".equals(kycStatus);
    }
}
