package com.finotech.jewellery.modules.product.domain.entity;

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
 * Catalogue artwork for a design — what a customer points at before any item
 * of it exists. Same shape as {@link ProductImage}; only the reference is held
 * here, the binary sits in MinIO behind {@code /api/v1/files}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "design_image", schema = "product")
public class DesignImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_id", nullable = false)
    private JewelleryDesign design;

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
