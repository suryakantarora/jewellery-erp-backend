package com.finotech.jewellery.modules.supplier.domain.entity;

import com.finotech.jewellery.modules.supplier.domain.enums.SupplierStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A vendor the business buys jewellery, metal or stones from.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "supplier", schema = "procurement",
        uniqueConstraints = @UniqueConstraint(name = "uq_supplier_company_code",
                columnNames = {"company_id", "code"}))
public class Supplier extends BaseEntity {

    /**
     * The owning company (tenant). Held as an id, not an association: the
     * organization module owns companies. Never changes after creation.
     */
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "tax_number", length = 50)
    private String taxNumber;

    @Column(name = "supplier_type", length = 30)
    private String supplierType;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    /** Agreed settlement window, used to date supplier invoices. */
    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<SupplierContact> contacts = new ArrayList<>();

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<SupplierBankAccount> bankAccounts = new ArrayList<>();

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<SupplierDocument> documents = new ArrayList<>();

    public void addContact(SupplierContact contact) {
        contact.setSupplier(this);
        contacts.add(contact);
    }

    public void addBankAccount(SupplierBankAccount account) {
        account.setSupplier(this);
        bankAccounts.add(account);
    }

    public void addDocument(SupplierDocument document) {
        document.setSupplier(this);
        documents.add(document);
    }

    public boolean canTrade() {
        return status == SupplierStatus.ACTIVE;
    }
}
