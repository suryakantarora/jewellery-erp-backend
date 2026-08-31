package com.finotech.jewellery.modules.organization.domain.entity;

import com.finotech.jewellery.modules.organization.domain.enums.OrganizationStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The legal entity that owns branches. Multi-company is supported from day one
 * so a group structure does not require a later migration.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "company", schema = "organization")
public class Company extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "tax_number", length = 50)
    private String taxNumber;

    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency = "LAK";

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;
}
