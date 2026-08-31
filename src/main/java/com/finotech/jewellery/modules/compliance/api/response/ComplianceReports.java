package com.finotech.jewellery.modules.compliance.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Regulatory and control reports (section 23).
 */
public final class ComplianceReports {

    private ComplianceReports() {
    }

    /**
     * Sales at or above the reportable threshold, with the customer's KYC state
     * alongside — a high-value sale to an unverified customer is the case a
     * regulator asks about.
     */
    public record HighValueTransactionReport(LocalDate from, LocalDate to, BigDecimal threshold,
                                             int transactionCount, BigDecimal totalValue,
                                             int unverifiedCustomerCount,
                                             List<HighValueTransaction> transactions) {

        public record HighValueTransaction(UUID saleId, String saleNumber, String invoiceNumber,
                                           LocalDate saleDate, UUID branchId, UUID customerId,
                                           String customerName, String customerCode,
                                           String kycStatus, boolean kycVerified,
                                           BigDecimal totalAmount, BigDecimal cashAmount,
                                           boolean cashReportable) {
        }
    }

    /**
     * KYC posture across the customer base: who is verified, who is pending, and
     * which documents have expired.
     */
    public record KycStatusReport(LocalDate asOf, long totalCustomers, long verified,
                                  long pending, long rejected, long notRequired, long expired,
                                  long verifiedWithExpiredDocuments,
                                  List<KycException> exceptions) {

        /** A customer whose KYC state needs attention. */
        public record KycException(UUID customerId, String customerCode, String customerName,
                                   String kycStatus, String issue, LocalDate documentExpiry,
                                   BigDecimal lifetimeSpend) {
        }
    }

    /**
     * Everything that passed through a dual-authorisation control: vault
     * movements, stock counts and buyback valuations.
     */
    public record DualAuthorisationReport(LocalDate from, LocalDate to, int totalControlled,
                                          int fullyAuthorised, int singleApproverOnly,
                                          List<ControlledAction> actions) {

        public record ControlledAction(String controlType, UUID entityId, String reference,
                                       UUID branchId, String firstApprover, Instant firstApprovedAt,
                                       String secondApprover, Instant secondApprovedAt,
                                       String status, boolean fullyAuthorised, String note) {
        }
    }

    /**
     * Who did what, over the audit trail. Used to answer "show me every price
     * change this month" without reading raw audit rows.
     */
    public record AuditSummaryReport(Instant from, Instant to, long totalEvents,
                                     List<ActionCount> byAction, List<ActorCount> byUser,
                                     List<SensitiveEvent> sensitiveEvents) {

        public record ActionCount(String action, String entityType, long count) {
        }

        public record ActorCount(String username, long count, long sensitiveCount) {
        }

        /** An action worth a second look: money moved, or a control overridden. */
        public record SensitiveEvent(UUID id, String username, String action, String entityType,
                                     String entityId, UUID branchId, Instant occurredAt) {
        }
    }
}
