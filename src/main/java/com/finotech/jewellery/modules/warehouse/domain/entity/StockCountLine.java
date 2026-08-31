package com.finotech.jewellery.modules.warehouse.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One item on a count sheet, and what the count found.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "stock_count_line", schema = "inventory")
public class StockCountLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_count_id", nullable = false)
    private StockCount stockCount;

    @Column(name = "jewellery_item_id", nullable = false)
    private UUID jewelleryItemId;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    /** True if the system expected this item at the location. */
    @Column(name = "expected", nullable = false)
    private boolean expected;

    /** True if it was physically found during the count. */
    @Column(name = "counted", nullable = false)
    private boolean counted;

    @Column(name = "bin_id")
    private UUID binId;

    @Column(name = "variance_note", length = 500)
    private String varianceNote;

    /** Expected but not found — the case that matters most in a vault. */
    public boolean isMissing() {
        return expected && !counted;
    }

    /** Found but not expected here, e.g. a misplaced transfer. */
    public boolean isUnexpected() {
        return !expected && counted;
    }
}
