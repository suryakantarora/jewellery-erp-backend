package com.finotech.jewellery.modules.finance.application.service;

import com.finotech.jewellery.modules.finance.api.request.FinanceRequests;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.AccountResponse;
import com.finotech.jewellery.modules.finance.api.response.FinanceReports.JournalEntryResponse;
import com.finotech.jewellery.modules.finance.domain.entity.Account;
import com.finotech.jewellery.modules.finance.domain.entity.JournalEntry;
import com.finotech.jewellery.modules.finance.domain.entity.JournalEntryLine;
import com.finotech.jewellery.modules.finance.domain.enums.JournalSource;
import com.finotech.jewellery.modules.finance.domain.enums.JournalStatus;
import com.finotech.jewellery.modules.finance.infrastructure.repository.AccountRepository;
import com.finotech.jewellery.modules.finance.infrastructure.repository.JournalEntryRepository;
import com.finotech.jewellery.modules.finance.infrastructure.repository.LedgerQueryRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Chart of accounts and the journal, including hand-entered corrections.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int LEDGER_LINE_LIMIT = 500;

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalRepository;
    private final LedgerQueryRepository ledgerQueries;
    private final JournalPostingService posting;
    private final AuditService auditService;

    // ---------- chart of accounts ----------

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        return accountRepository.findAllByOrderByCodeAsc().stream()
                .map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse createAccount(FinanceRequests.AccountRequest request) {
        if (accountRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Account code already exists: " + request.code());
        }
        Account account = new Account();
        account.setCode(request.code().trim());
        account.setName(request.name().trim());
        account.setAccountType(request.accountType());
        account.setPostable(request.postable());
        account.setCompanyId(request.companyId());
        account.setDescription(request.description());
        if (StringUtils.hasText(request.currency())) {
            account.setCurrency(request.currency().toUpperCase());
        }
        if (request.parentId() != null) {
            Account parent = accountRepository.findById(request.parentId())
                    .orElseThrow(() -> NotFoundException.of("Account", request.parentId()));
            if (parent.getAccountType() != request.accountType()) {
                throw new ValidationException(
                        "A child account must be the same type as its parent");
            }
            account.setParent(parent);
        }

        Account saved = accountRepository.save(account);
        auditService.record("ACCOUNT_CREATED", "Account", saved.getId(), null,
                AccountResponse.from(saved));
        return AccountResponse.from(saved);
    }

    @Transactional
    public AccountResponse deactivateAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Account", id));
        if (account.isSystemAccount()) {
            throw new ValidationException("Account " + account.getCode()
                    + " is required by the automatic postings and cannot be deactivated");
        }
        account.setActive(false);
        return AccountResponse.from(account);
    }

    // ---------- journal ----------

    @Transactional(readOnly = true)
    public PageResponse<JournalEntryResponse> searchEntries(JournalStatus status,
                                                            JournalSource source, UUID branchId,
                                                            LocalDate from, LocalDate to,
                                                            Pageable pageable) {
        return PageResponse.of(journalRepository.search(status, source, branchId, from, to, pageable),
                JournalEntryResponse::summary);
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getEntry(UUID id) {
        return JournalEntryResponse.from(journalRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("JournalEntry", id)));
    }

    @Transactional(readOnly = true)
    public List<LedgerQueryRepository.LedgerLineRow> accountLedger(UUID accountId, LocalDate from,
                                                                   LocalDate to) {
        return ledgerQueries.accountLedger(accountId, from, to, LEDGER_LINE_LIMIT);
    }

    /**
     * Posts a hand-entered journal.
     *
     * <p>Each line must be a debit or a credit, not both: a line that is both is
     * almost always a mistake, and silently netting it would hide the error.
     */
    @Transactional
    public JournalEntryResponse postManualEntry(FinanceRequests.ManualJournalRequest request) {
        JournalEntry journal = new JournalEntry();
        journal.setEntryNumber(CodeGenerator.reference("JE"));
        journal.setEntryDate(request.entryDate());
        journal.setSource(JournalSource.MANUAL);
        journal.setDescription(request.description().trim());
        journal.setBranchId(request.branchId());

        int order = 0;
        for (FinanceRequests.ManualJournalRequest.Line requested : request.lines()) {
            BigDecimal debit = MoneyUtils.nullSafe(requested.debit());
            BigDecimal credit = MoneyUtils.nullSafe(requested.credit());

            if (debit.signum() > 0 && credit.signum() > 0) {
                throw new ValidationException("Line " + (order + 1)
                        + " has both a debit and a credit; use two lines instead");
            }
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw new ValidationException("Line " + (order + 1) + " is for zero");
            }

            Account account = accountRepository.findByCodeIgnoreCase(requested.accountCode())
                    .orElseThrow(() -> new NotFoundException(
                            "No account with code " + requested.accountCode()));
            if (!account.isPostable()) {
                throw new ValidationException("Account " + account.getCode()
                        + " is a heading and cannot be posted to");
            }
            if (!account.isActive()) {
                throw new ValidationException("Account " + account.getCode() + " is not active");
            }

            JournalEntryLine line = new JournalEntryLine();
            line.setAccount(account);
            line.setDebit(MoneyUtils.money(debit));
            line.setCredit(MoneyUtils.money(credit));
            line.setDescription(requested.description());
            line.setLineOrder(order++);
            journal.addLine(line);
        }

        journal.validateAndTotal();
        journal.setStatus(JournalStatus.POSTED);
        journal.setPostedAt(Instant.now());
        journal.setPostedBy(SecurityUtils.currentUsername().orElse("system"));

        JournalEntry saved = journalRepository.save(journal);
        auditService.record("MANUAL_JOURNAL_POSTED", "JournalEntry", saved.getId(), null,
                Map.of("entryNumber", saved.getEntryNumber(), "amount", saved.getTotalDebit(),
                        "description", saved.getDescription()),
                saved.getBranchId());
        return JournalEntryResponse.from(saved);
    }

    @Transactional
    public JournalEntryResponse reverseEntry(UUID id, String reason) {
        return JournalEntryResponse.from(posting.reverse(id, reason));
    }
}
