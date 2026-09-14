package com.finotech.jewellery.modules.customer.domain.entity;

import com.finotech.jewellery.modules.customer.domain.enums.CustomerStatus;
import com.finotech.jewellery.modules.customer.domain.enums.CustomerType;
import com.finotech.jewellery.modules.customer.domain.enums.KycStatus;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The customer master. Behavioural data (follow-ups, campaigns, segments) lives
 * in the CRM module, keeping this record a stable identity (section 17).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customer", schema = "customer",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_customer_company_code",
                        columnNames = {"company_id", "customer_code"}),
                @UniqueConstraint(name = "uq_customer_company_phone",
                        columnNames = {"company_id", "phone"})})
public class Customer extends BaseEntity {

    /**
     * The owning company (tenant). Held as an id, not an association: the
     * organization module owns companies. Never changes after creation.
     */
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "customer_code", nullable = false, length = 30)
    private String customerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType = CustomerType.INDIVIDUAL;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "alternate_phone", length = 30)
    private String alternatePhone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "anniversary_date")
    private LocalDate anniversaryDate;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "tax_number", length = 50)
    private String taxNumber;

    /** Branch the customer was first registered at; not an access restriction. */
    @Column(name = "registered_branch_id")
    private UUID registeredBranchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus = KycStatus.NOT_REQUIRED;

    @Column(name = "kyc_verified_at")
    private LocalDate kycVerifiedAt;

    @Column(name = "kyc_verified_by", length = 100)
    private String kycVerifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<CustomerAddress> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<CustomerDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<CustomerPreference> preferences = new ArrayList<>();

    public void addAddress(CustomerAddress address) {
        address.setCustomer(this);
        addresses.add(address);
    }

    public void addDocument(CustomerDocument document) {
        document.setCustomer(this);
        documents.add(document);
    }

    public void addPreference(CustomerPreference preference) {
        preference.setCustomer(this);
        preferences.add(preference);
    }

    public boolean canTransact() {
        return status == CustomerStatus.ACTIVE;
    }

    public boolean isKycVerified() {
        return kycStatus == KycStatus.VERIFIED || kycStatus == KycStatus.NOT_REQUIRED;
    }
}
