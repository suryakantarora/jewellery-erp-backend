package com.finotech.jewellery.modules.inventory.domain.entity;

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
 * A photograph of one physical piece.
 *
 * <p>Distinct from {@code ProductImage}, which is catalogue artwork shared by
 * every item made to a product. Staff identifying stock in a tray need the
 * piece in front of them, not the catalogue shot.
 *
 * <p>Only the storage reference lives in Postgres; the binary sits in MinIO
 * behind {@code /api/v1/files}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "item_image", schema = "inventory")
public class ItemImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jewellery_item_id", nullable = false)
    private JewelleryItem item;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "primary_image", nullable = false)
    private boolean primaryImage;

    @Column(name = "display_order")
    private Integer displayOrder;
}
