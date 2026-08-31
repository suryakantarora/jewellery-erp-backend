package com.finotech.jewellery.modules.organization.domain.entity;

import com.finotech.jewellery.modules.organization.domain.enums.LocationType;
import com.finotech.jewellery.modules.organization.domain.enums.OrganizationStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A stock-holding place inside a branch: showroom, counter, vault, store room
 * or warehouse. Locations form a tree via {@code parent}, e.g.
 * Branch Warehouse → Showroom → Counter.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "location", schema = "organization")
public class Location extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Location parent;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private LocationType type;

    /** Vault and store rooms may require two approvers for issue/return. */
    @Column(name = "dual_authorization", nullable = false)
    private boolean dualAuthorization;

    /**
     * Raise a low-stock alert when the count of available items here falls to or
     * below this. Null means the location is not monitored — a vault holding two
     * pieces is not low on stock, it is a vault.
     */
    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }
}
