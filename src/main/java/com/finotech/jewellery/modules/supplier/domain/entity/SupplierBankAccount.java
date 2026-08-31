package com.finotech.jewellery.modules.supplier.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Settlement details for supplier payments.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "supplier_bank_account", schema = "procurement")
public class SupplierBankAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "bank_name", nullable = false, length = 150)
    private String bankName;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "branch_name", length = 150)
    private String branchName;

    @Column(name = "swift_code", length = 20)
    private String swiftCode;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "primary_account", nullable = false)
    private boolean primaryAccount;
}
