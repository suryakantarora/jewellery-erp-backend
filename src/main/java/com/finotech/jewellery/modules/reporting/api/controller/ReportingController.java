package com.finotech.jewellery.modules.reporting.api.controller;

import com.finotech.jewellery.modules.reporting.api.response.OperationalReports.InventoryValuationReport;
import com.finotech.jewellery.modules.reporting.api.response.OperationalReports.LoyaltyLiabilityReport;
import com.finotech.jewellery.modules.reporting.api.response.OperationalReports.SalesReport;
import com.finotech.jewellery.modules.reporting.api.response.OperationalReports.StockAgeingReport;
import com.finotech.jewellery.modules.reporting.application.service.ReportingService;
import com.finotech.jewellery.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reporting")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportingController {

    private static final String VIEW = "hasAuthority('REPORT_VIEW')";

    private final ReportingService reportingService;

    @Operation(summary = "Sales report",
            description = "Totals for a period with breakdowns by day, branch, product type "
                    + "and salesperson. Covers settled sales only.")
    @GetMapping("/sales")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<SalesReport>> sales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(
                reportingService.salesReport(from, to, branchId)));
    }

    @Operation(summary = "Inventory valuation",
            description = "Stock on hand at cost and at today's metal rate, broken down by "
                    + "location, metal/purity and status.")
    @GetMapping("/inventory-valuation")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<InventoryValuationReport>> inventoryValuation(
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(reportingService.inventoryValuation(branchId)));
    }

    @Operation(summary = "Stock ageing",
            description = "Sellable stock bucketed by how long it has been held, with the "
                    + "oldest items listed.")
    @GetMapping("/stock-ageing")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<StockAgeingReport>> stockAgeing(
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(reportingService.stockAgeing(branchId)));
    }

    @Operation(summary = "Loyalty liability",
            description = "Outstanding points and what honouring them would cost, with the "
                    + "portion expiring within 90 days shown separately.")
    @GetMapping("/loyalty-liability")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<LoyaltyLiabilityReport>> loyaltyLiability() {
        return ResponseEntity.ok(ApiResponse.ok(reportingService.loyaltyLiability()));
    }
}
