package com.finotech.jewellery.modules.customer.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Something a customer liked: a specific piece, a product, or a design. Any
 * colleague at any branch can see it when the customer comes back, which is
 * what a device-local list could never do.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customer_wishlist", schema = "customer")
public class CustomerWishlistEntry extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "jewellery_item_id")
    private UUID jewelleryItemId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "design_id")
    private UUID designId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "added_by", length = 100)
    private String addedBy;

    @Column(name = "branch_id")
    private UUID branchId;
}
