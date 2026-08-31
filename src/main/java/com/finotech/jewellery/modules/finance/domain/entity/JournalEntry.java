package com.finotech.jewellery.modules.finance.domain.entity;

import com.finotech.jewellery.modules.finance.domain.enums.JournalSource;
import com.finotech.jewellery.modules.finance.domain.enums.JournalStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import com.finotech.jewellery.shared.exception.ValidationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * A double-entry journal.
 *
 * <p>An entry is only meaningful if its debits equal its credits, so that is
 * checked when it is posted rather than trusted. Once posted an entry is
 * immutable: a mistake is corrected by a reversing entry, never by editing
 * history.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "journal_entry", schema = "finance")
public class JournalEntry extends BaseEntity {

    @Column(name = "entry_number", nullable = false, unique = true, length = 50)
    private String entryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private JournalSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JournalStatus status = JournalStatus.DRAFT;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /** The business document this entry accounts for. */
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by", length = 100)
    private String postedBy;

    /** Set on both sides of a reversal so each points at the other. */
    @Column(name = "reversal_of_id")
    private UUID reversalOfId;

    @Column(name = "reversed_by_id")
    private UUID reversedById;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<JournalEntryLine> lines = new ArrayList<>();

    public void addLine(JournalEntryLine line) {
        line.setJournalEntry(this);
        lines.add(line);
    }

    /**
     * Totals the lines and refuses an entry that does not balance.
     *
     * @throws ValidationException if debits and credits differ, or there is
     *         nothing to post
     */
    public void validateAndTotal() {
        if (lines.isEmpty()) {
            throw new ValidationException("A journal entry must have at least one line");
        }
        BigDecimal debit = lines.stream().map(JournalEntryLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = lines.stream().map(JournalEntryLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debit.compareTo(credit) != 0) {
            throw new ValidationException("Journal entry does not balance: debits " + debit
                    + " against credits " + credit);
        }
        if (debit.signum() == 0) {
            throw new ValidationException("A journal entry cannot be for zero");
        }
        this.totalDebit = debit;
        this.totalCredit = credit;
    }

    public boolean isPosted() {
        return status == JournalStatus.POSTED;
    }
}
