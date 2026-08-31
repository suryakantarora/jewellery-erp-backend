package com.finotech.jewellery.modules.compliance.api.controller;

import com.finotech.jewellery.modules.compliance.api.response.ComplianceReports.AuditSummaryReport;
import com.finotech.jewellery.modules.compliance.api.response.ComplianceReports.DualAuthorisationReport;
import com.finotech.jewellery.modules.compliance.api.response.ComplianceReports.HighValueTransactionReport;
import com.finotech.jewellery.modules.compliance.api.response.ComplianceReports.KycStatusReport;
import com.finotech.jewellery.modules.compliance.application.service.ComplianceReportService;
import com.finotech.jewellery.modules.compliance.infrastructure.repository.ComplianceQueryRepository;
import com.finotech.jewellery.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Compliance")
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private static final String REPORT = "hasAuthority('COMPLIANCE_REPORT')";

    private final ComplianceReportService complianceService;

    @Operation(summary = "Reportable high-value transactions",
            description = "Sales at or above the reportable threshold, with the customer's "
                    + "KYC state and any cash component alongside.")
    @GetMapping("/reports/high-value-transactions")
    @PreAuthorize(REPORT)
    public ResponseEntity<ApiResponse<HighValueTransactionReport>> highValueTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) BigDecimal threshold) {
        return ResponseEntity.ok(ApiResponse.ok(
                complianceService.highValueTransactions(from, to, branchId, threshold)));
    }

    @Operation(summary = "Possible structuring",
            description = "Customers with repeated purchases just below the reporting "
                    + "threshold. This flags a pattern for review; it is not a determination.")
    @GetMapping("/reports/possible-structuring")
    @PreAuthorize(REPORT)
    public ResponseEntity<ApiResponse<List<ComplianceQueryRepository.StructuringRow>>>
            possibleStructuring() {
        return ResponseEntity.ok(ApiResponse.ok(complianceService.possibleStructuring()));
    }

    @Operation(summary = "KYC status across the customer base",
            description = "Counts by state, plus the customers needing attention — "
                    + "pending review, rejected, or verified against an expired document.")
    @GetMapping("/reports/kyc-status")
    @PreAuthorize(REPORT)
    public ResponseEntity<ApiResponse<KycStatusReport>> kycStatus() {
        return ResponseEntity.ok(ApiResponse.ok(complianceService.kycStatus()));
    }

    @Operation(summary = "Dual-authorisation controls",
            description = "Vault movements, stock counts and buyback valuations, showing "
                    + "whether each actually received two approvers.")
    @GetMapping("/reports/dual-authorisation")
    @PreAuthorize(REPORT)
    public ResponseEntity<ApiResponse<DualAuthorisationReport>> dualAuthorisation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(
                complianceService.dualAuthorisation(from, to, branchId)));
    }

    @Operation(summary = "Audit trail summary",
            description = "Who did what over a period, by action and by user, with "
                    + "sensitive actions called out. Defaults to the last 30 days.")
    @GetMapping("/reports/audit-summary")
    @PreAuthorize(REPORT)
    public ResponseEntity<ApiResponse<AuditSummaryReport>> auditSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(30, ChronoUnit.DAYS) : from;
        return ResponseEntity.ok(ApiResponse.ok(complianceService.auditSummary(start, end)));
    }
}
