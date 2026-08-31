package com.finotech.jewellery.modules.finance.domain.entity;

import com.finotech.jewellery.modules.finance.domain.enums.AccountType;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of the chart of accounts.
 *
 * <p>Accounts nest through {@code parent} for presentation, but only accounts
 * marked {@code postable} may appear on a journal line — posting to a heading
 * would make its children meaningless.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "account", schema = "finance")
public class Account extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Account parent;

    /** False for headings that only group their children. */
    @Column(name = "postable", nullable = false)
    private boolean postable = true;

    /** Seeded accounts the posting rules depend on; they cannot be deleted. */
    @Column(name = "system_account", nullable = false)
    private boolean systemAccount;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public boolean isDebitNormal() {
        return accountType.isDebitNormal();
    }
}
