package com.finotech.jewellery.modules.warehouse.domain.entity;

import com.finotech.jewellery.modules.warehouse.domain.enums.BinType;
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
 * A place inside a location: Warehouse → Vault → Zone → Shelf → Tray.
 *
 * <p>Bins nest through {@code parent} and always belong to one organization
 * location, so an item's address is location plus bin. Inventory remains the
 * source of truth for which location an item is in; the bin refines where.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "storage_bin", schema = "inventory")
public class StorageBin extends BaseEntity {

    /** Owning location; a bin never spans two locations. */
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private StorageBin parent;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "bin_type", nullable = false, length = 20)
    private BinType binType;

    /** Advisory limit used to flag over-filling, not enforced as a hard cap. */
    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
