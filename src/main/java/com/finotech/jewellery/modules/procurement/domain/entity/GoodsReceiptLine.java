package com.finotech.jewellery.modules.procurement.domain.entity;

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
 * One physical piece received. Each line becomes exactly one serialized item,
 * so the weight recorded here is the weight the item carries for life.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "goods_receipt_line", schema = "procurement")
public class GoodsReceiptLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @Column(name = "purchase_order_line_id", nullable = false)
    private UUID purchaseOrderLineId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "metal_id")
    private UUID metalId;

    @Column(name = "purity_id")
    private UUID purityId;

    @Column(name = "gross_weight", nullable = false, precision = 12, scale = 3)
    private BigDecimal grossWeight;

    @Column(name = "stone_weight", precision = 12, scale = 3)
    private BigDecimal stoneWeight;

    @Column(name = "purchase_cost", precision = 19, scale = 4)
    private BigDecimal purchaseCost;

    @Column(name = "making_cost", precision = 19, scale = 4)
    private BigDecimal makingCost;

    @Column(name = "stone_cost", precision = 19, scale = 4)
    private BigDecimal stoneCost;

    @Column(name = "hallmark_number", length = 50)
    private String hallmarkNumber;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "rfid_tag", length = 100)
    private String rfidTag;

    /** Set once the receipt is accepted and the item exists. */
    @Column(name = "jewellery_item_id")
    private UUID jewelleryItemId;

    @Column(name = "notes", length = 255)
    private String notes;
}
