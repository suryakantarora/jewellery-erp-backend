package com.finotech.jewellery.modules.finance.api.controller;

import com.finotech.jewellery.modules.finance.api.request.FinanceRequests;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.AccountResponse;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.AgeingReport;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.BalanceSheetReport;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.JournalEntryResponse;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.ProfitAndLossReport;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.TrialBalanceReport;
import com.finotech.jewellery.modules.finance.application.service.AccountService;
import com.finotech.jewellery.modules.finance.application.service.FinanceReportService;
import com.finotech.jewellery.modules.finance.domain.enums.JournalSource;
import com.finotech.jewellery.modules.finance.domain.enums.JournalStatus;
import com.finotech.jewellery.modules.finance.infrastructure.repository.LedgerQueryRepository;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Finance")
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {

    private static final String VIEW = "hasAuthority('FINANCE_VIEW')";
    private static final String MANAGE = "hasAuthority('FINANCE_MANAGE')";
    private static final String POST = "hasAuthority('FINANCE_POST')";

    private final AccountService accountService;
    private final FinanceReportService reportService;

    // ---------- chart of accounts ----------

    @Operation(summary = "List the chart of accounts")
    @GetMapping("/accounts")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<AccountResponse>>> listAccounts() {
        return ResponseEntity.ok(ApiResponse.ok(accountService.listAccounts()));
    }

    @Operation(summary = "Create an account")
    @PostMapping("/accounts")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody FinanceRequests.AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(accountService.createAccount(request)));
    }

    @Operation(summary = "Deactivate an account",
            description = "Accounts the automatic postings depend on cannot be deactivated.")
    @DeleteMapping("/accounts/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AccountResponse>> deactivateAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.deactivateAccount(id)));
    }

    @Operation(summary = "Every posted movement on one account")
    @GetMapping("/accounts/{id}/ledger")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<LedgerQueryRepository.LedgerLineRow>>> accountLedger(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.accountLedger(id, from, to)));
    }

    // ---------- journal ----------

    @Operation(summary = "Search journal entries")
    @GetMapping("/journal-entries")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<PageResponse<JournalEntryResponse>>> searchEntries(
            @RequestParam(required = false) JournalStatus status,
            @RequestParam(required = false) JournalSource source,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                accountService.searchEntries(status, source, branchId, from, to, pageable)));
    }

    @Operation(summary = "Get a journal entry with its lines")
    @GetMapping("/journal-entries/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<JournalEntryResponse>> getEntry(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getEntry(id)));
    }

    @Operation(summary = "Post a manual journal",
            description = "Debits must equal credits. Each line is either a debit or a "
                    + "credit, never both.")
    @PostMapping("/journal-entries")
    @PreAuthorize(POST)
    public ResponseEntity<ApiResponse<JournalEntryResponse>> postManualEntry(
            @Valid @RequestBody FinanceRequests.ManualJournalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(accountService.postManualEntry(request)));
    }

    @Operation(summary = "Reverse a posted entry",
            description = "Writes an equal and opposite entry. The original is never edited.")
    @PostMapping("/journal-entries/{id}/reverse")
    @PreAuthorize(POST)
    public ResponseEntity<ApiResponse<JournalEntryResponse>> reverseEntry(
            @PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.reverseEntry(id, reason)));
    }

    // ---------- statements ----------

    @Operation(summary = "Trial balance",
            description = "Opening balance and period movements per account, with the "
                    + "debit/credit balance check.")
    @GetMapping("/reports/trial-balance")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<TrialBalanceReport>> trialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.trialBalance(from, to, branchId)));
    }

    @Operation(summary = "Profit and loss")
    @GetMapping("/reports/profit-and-loss")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<ProfitAndLossReport>> profitAndLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.profitAndLoss(from, to, branchId)));
    }

    @Operation(summary = "Balance sheet",
            description = "Position at a date. Retained earnings are derived from the ledger, "
                    + "and whether the sheet balances is reported rather than assumed.")
    @GetMapping("/reports/balance-sheet")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<BalanceSheetReport>> balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.ok(
                reportService.balanceSheet(asOf == null ? LocalDate.now() : asOf, branchId)));
    }

    @Operation(summary = "Receivables ageing")
    @GetMapping("/reports/receivables-ageing")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<AgeingReport>> receivablesAgeing() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.receivablesAgeing()));
    }
}
