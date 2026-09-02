package com.finotech.jewellery.modules.inventory.domain.entity;

import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import com.finotech.jewellery.shared.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The unique, serialized physical item — the central entity of the platform
 * (section 15). Every ring, chain and bangle in the business is one row here.
 *
 * <p>Inventory owns current location and status; no other module writes them
 * directly (dependency rule 2). Cross-module references (product, metal,
 * purity, location, customer) are held as ids so each module keeps its tables.
 *
 * <p>The inherited {@code version} column gives optimistic locking, and sale
 * and reservation paths additionally take a pessimistic row lock so two
 * cashiers cannot sell the same item.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "jewellery_item", schema = "inventory")
public class JewelleryItem extends BaseEntity {

    /** Human-readable serial, e.g. {@code ITM-20260830-K7X4Q2}. */
    @Column(name = "item_code", nullable = false, unique = true, length = 50)
    private String itemCode;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "design_id")
    private UUID designId;

    // ---------- physical identity ----------

    @Column(name = "metal_id", nullable = false)
    private UUID metalId;

    @Column(name = "purity_id", nullable = false)
    private UUID purityId;

    @Column(name = "gross_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal grossWeight;

    /** grossWeight − stoneWeight; the weight metal is charged on. */
    @Column(name = "net_metal_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal netMetalWeight;

    @Column(name = "stone_weight", precision = 12, scale = 3)
    private BigDecimal stoneWeight = BigDecimal.ZERO;

    @Column(name = "stone_count")
    private int stoneCount;

    @Column(name = "total_carat", precision = 10, scale = 3)
    private BigDecimal totalCarat = BigDecimal.ZERO;

    @Column(name = "size_id")
    private UUID sizeId;

    // ---------- digital passport tags ----------

    @Column(name = "rfid_tag", unique = true, length = 100)
    private String rfidTag;

    @Column(name = "qr_code", unique = true, length = 100)
    private String qrCode;

    @Column(name = "barcode", unique = true, length = 100)
    private String barcode;

    @Column(name = "hallmark_number", length = 50)
    private String hallmarkNumber;

    // ---------- cost and price ----------

    @Column(name = "purchase_cost", precision = 19, scale = 4)
    private BigDecimal purchaseCost;

    @Column(name = "making_cost", precision = 19, scale = 4)
    private BigDecimal makingCost;

    @Column(name = "stone_cost", precision = 19, scale = 4)
    private BigDecimal stoneCost;

    @Column(name = "total_cost", precision = 19, scale = 4)
    private BigDecimal totalCost;

    /** Latest calculated selling price; historical sale prices are kept on the sale. */
    @Column(name = "current_price", precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "price_calculated_at")
    private Instant priceCalculatedAt;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    // ---------- current state ----------

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ItemStatus status = ItemStatus.DRAFT;

    @Column(name = "current_location_id")
    private UUID currentLocationId;

    /**
     * The bin the piece is stored in, within its current location.
     *
     * <p>Nullable on purpose: a showroom counter needs no bin, and stock that
     * has not been put away yet genuinely has none. An item is never blocked
     * from existing because nobody has assigned it a tray.
     */
    @Column(name = "bin_id")
    private UUID binId;

    /**
     * Photographs of this piece, ordered as they should be shown.
     *
     * <p>Batched deliberately. A page of thirty search results each reading its
     * own images would fire thirty extra queries; {@code @BatchSize} collapses
     * those into one. The list response needs only the primary key, but it
     * needs it for every row.
     */
    @OrderBy("displayOrder asc, createdAt asc")
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ItemImage> images = new ArrayList<>();

    @Column(name = "current_branch_id")
    private UUID currentBranchId;

    /** Set while RESERVED; cleared on release or sale. */
    @Column(name = "reserved_for_customer_id")
    private UUID reservedForCustomerId;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "reserved_by", length = 100)
    private String reservedBy;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "sold_date")
    private LocalDate soldDate;

    @Column(name = "owner_customer_id")
    private UUID ownerCustomerId;

    @Column(name = "quality_checked", nullable = false)
    private boolean qualityChecked;

    @Column(name = "notes", length = 500)
    private String notes;

    // ---------- behaviour ----------

    /**
     * Applies a status change, enforcing the transition table.
     *
     * @throws ConflictException if the transition is not permitted
     */
    public void transitionTo(ItemStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException(
                    "Item " + itemCode + " cannot move from " + status + " to " + target);
        }
        this.status = target;
    }

    public boolean isReservationActive() {
        return status == ItemStatus.RESERVED
                && (reservedUntil == null || reservedUntil.isAfter(Instant.now()));
    }

    public void clearReservation() {
        this.reservedForCustomerId = null;
        this.reservedUntil = null;
        this.reservedBy = null;
    }

    /** Recomputes net metal weight whenever gross or stone weight changes. */
    public void recalculateNetMetalWeight() {
        BigDecimal stones = stoneWeight == null ? BigDecimal.ZERO : stoneWeight;
        this.netMetalWeight = grossWeight.subtract(stones);
    }
}
