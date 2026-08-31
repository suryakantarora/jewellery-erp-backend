package com.finotech.jewellery.modules.finance.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One side of a journal entry.
 *
 * <p>Debit and credit are separate columns rather than one signed amount: it is
 * how ledgers are read, and it makes an accidental sign error impossible to
 * express.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "journal_entry_line", schema = "finance")
public class JournalEntryLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "description", length = 500)
    private String description;

    /** Whose balance this line affects, for receivables and payables ageing. */
    @Column(name = "party_type", length = 20)
    private String partyType;

    @Column(name = "party_id")
    private UUID partyId;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    /** Signed movement, positive for a debit. */
    public BigDecimal signedAmount() {
        return debit.subtract(credit);
    }
}
