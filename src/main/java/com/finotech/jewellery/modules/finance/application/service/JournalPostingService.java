package com.finotech.jewellery.modules.finance.application.service;

import com.finotech.jewellery.modules.finance.domain.entity.Account;
import com.finotech.jewellery.modules.finance.domain.entity.JournalEntry;
import com.finotech.jewellery.modules.finance.domain.entity.JournalEntryLine;
import com.finotech.jewellery.modules.finance.domain.enums.JournalSource;
import com.finotech.jewellery.modules.finance.domain.enums.JournalStatus;
import com.finotech.jewellery.modules.finance.domain.enums.StandardAccount;
import com.finotech.jewellery.modules.finance.infrastructure.repository.AccountRepository;
import com.finotech.jewellery.modules.finance.infrastructure.repository.JournalEntryRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.SecurityUtils;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import com.finotech.jewellery.shared.utils.MoneyUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds and posts journal entries.
 *
 * <p>Every automatic posting goes through here, so there is exactly one place
 * where the ledger is written and exactly one place that enforces the rules:
 * an entry must balance, may only touch postable accounts, and is immutable once
 * posted. A mistake is corrected by a reversing entry.
 */
@Service
@RequiredArgsConstructor
public class JournalPostingService {

    private final JournalEntryRepository journalRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    /** Collects lines before they become an entry, keeping call sites readable. */
    public static final class EntryBuilder {

        private final List<PendingLine> pending = new ArrayList<>();

        private record PendingLine(StandardAccount account, BigDecimal debit, BigDecimal credit,
                                   String description, String partyType, UUID partyId) {
        }

        public EntryBuilder debit(StandardAccount account, BigDecimal amount, String description) {
            return debit(account, amount, description, null, null);
        }

        public EntryBuilder debit(StandardAccount account, BigDecimal amount, String description,
                                  String partyType, UUID partyId) {
            if (isPositive(amount)) {
                pending.add(new PendingLine(account, MoneyUtils.money(amount), BigDecimal.ZERO,
                        description, partyType, partyId));
            }
            return this;
        }

        public EntryBuilder credit(StandardAccount account, BigDecimal amount, String description) {
            return credit(account, amount, description, null, null);
        }

        public EntryBuilder credit(StandardAccount account, BigDecimal amount, String description,
                                   String partyType, UUID partyId) {
            if (isPositive(amount)) {
                pending.add(new PendingLine(account, BigDecimal.ZERO, MoneyUtils.money(amount),
                        description, partyType, partyId));
            }
            return this;
        }

        /**
         * Zero and null amounts are skipped rather than posted. A zero line
         * carries no information and would only clutter the ledger.
         */
        private static boolean isPositive(BigDecimal amount) {
            return amount != null && amount.signum() > 0;
        }

        public boolean isEmpty() {
            return pending.isEmpty();
        }
    }

    public static EntryBuilder entry() {
        return new EntryBuilder();
    }

    /**
     * Posts an entry for a business document.
     *
     * @param referenceId the document being accounted for; posting the same
     *                    document twice is refused so a redelivered event cannot
     *                    double the ledger
     * @return the posted entry, or {@code null} if there was nothing to post or
     *         it had already been posted
     */
    @Transactional
    public JournalEntry post(JournalSource source, String referenceType, Object referenceId,
                             LocalDate entryDate, String description, UUID branchId,
                             EntryBuilder builder) {
        if (builder.isEmpty()) {
            return null;
        }
        String reference = referenceId == null ? null : String.valueOf(referenceId);

        if (reference != null && journalRepository
                .existsByReferenceTypeAndReferenceIdAndSourceAndStatus(
                        referenceType, reference, source, JournalStatus.POSTED)) {
            return null;
        }

        JournalEntry journal = new JournalEntry();
        journal.setEntryNumber(CodeGenerator.reference("JE"));
        journal.setEntryDate(entryDate == null ? LocalDate.now() : entryDate);
        journal.setSource(source);
        journal.setDescription(description);
        journal.setReferenceType(referenceType);
        journal.setReferenceId(reference);
        journal.setBranchId(branchId);

        int order = 0;
        for (EntryBuilder.PendingLine pendingLine : builder.pending) {
            Account account = requireAccount(pendingLine.account());
            if (!account.isPostable()) {
                throw new ValidationException("Account " + account.getCode()
                        + " is a heading and cannot be posted to");
            }
            JournalEntryLine line = new JournalEntryLine();
            line.setAccount(account);
            line.setDebit(pendingLine.debit());
            line.setCredit(pendingLine.credit());
            line.setDescription(pendingLine.description());
            line.setPartyType(pendingLine.partyType());
            line.setPartyId(pendingLine.partyId());
            line.setLineOrder(order++);
            journal.addLine(line);
        }

        journal.validateAndTotal();
        journal.setStatus(JournalStatus.POSTED);
        journal.setPostedAt(Instant.now());
        journal.setPostedBy(SecurityUtils.currentUsername().orElse("system"));

        JournalEntry saved = journalRepository.save(journal);
        auditService.record("JOURNAL_POSTED", "JournalEntry", saved.getId(), null,
                Map.of("entryNumber", saved.getEntryNumber(), "source", source,
                        "amount", saved.getTotalDebit(),
                        "reference", String.valueOf(reference)),
                branchId);
        return saved;
    }

    /**
     * Reverses a posted entry with an equal and opposite one.
     *
     * <p>The original is left untouched: a ledger that can be edited is not a
     * ledger.
     */
    @Transactional
    public JournalEntry reverse(UUID journalEntryId, String reason) {
        JournalEntry original = journalRepository.findById(journalEntryId)
                .orElseThrow(() -> NotFoundException.of("JournalEntry", journalEntryId));

        if (original.getStatus() != JournalStatus.POSTED) {
            throw new ConflictException("Only a posted entry can be reversed");
        }
        if (original.getReversedById() != null) {
            throw new ConflictException("Entry " + original.getEntryNumber()
                    + " has already been reversed");
        }

        JournalEntry reversal = new JournalEntry();
        reversal.setEntryNumber(CodeGenerator.reference("JE"));
        reversal.setEntryDate(LocalDate.now());
        reversal.setSource(original.getSource());
        reversal.setDescription("Reversal of " + original.getEntryNumber() + ": " + reason);
        reversal.setReferenceType(original.getReferenceType());
        reversal.setReferenceId(original.getReferenceId());
        reversal.setBranchId(original.getBranchId());
        reversal.setReversalOfId(original.getId());

        int order = 0;
        for (JournalEntryLine line : original.getLines()) {
            JournalEntryLine mirrored = new JournalEntryLine();
            mirrored.setAccount(line.getAccount());
            // Swapped, which is what makes the pair net to nothing.
            mirrored.setDebit(line.getCredit());
            mirrored.setCredit(line.getDebit());
            mirrored.setDescription("Reversal: " + line.getDescription());
            mirrored.setPartyType(line.getPartyType());
            mirrored.setPartyId(line.getPartyId());
            mirrored.setLineOrder(order++);
            reversal.addLine(mirrored);
        }

        reversal.validateAndTotal();
        reversal.setStatus(JournalStatus.POSTED);
        reversal.setPostedAt(Instant.now());
        reversal.setPostedBy(SecurityUtils.currentUsername().orElse("system"));

        JournalEntry saved = journalRepository.save(reversal);
        original.setStatus(JournalStatus.REVERSED);
        original.setReversedById(saved.getId());

        auditService.record("JOURNAL_REVERSED", "JournalEntry", original.getId(), null,
                Map.of("reversalEntry", saved.getEntryNumber(), "reason", reason),
                original.getBranchId());
        return saved;
    }

    private Account requireAccount(StandardAccount standard) {
        return accountRepository.findByCodeIgnoreCase(standard.code())
                .orElseThrow(() -> new ValidationException(
                        "Chart of accounts is missing the required account " + standard.code()
                                + " (" + standard.name() + ")"));
    }
}
