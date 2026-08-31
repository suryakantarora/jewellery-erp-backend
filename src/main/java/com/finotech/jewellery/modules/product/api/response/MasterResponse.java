package com.finotech.jewellery.modules.product.api.response;

import com.finotech.jewellery.modules.product.domain.entity.Brand;
import com.finotech.jewellery.modules.product.domain.entity.Collection;
import com.finotech.jewellery.modules.product.domain.entity.ProductCategory;
import com.finotech.jewellery.modules.product.domain.entity.ProductType;
import com.finotech.jewellery.modules.product.domain.enums.MasterStatus;
import java.util.UUID;

/** One shape for the simple lookup masters returned to clients. */
public record MasterResponse(UUID id, String code, String name, String description,
                             UUID parentId, Boolean sizeable, Integer displayOrder,
                             MasterStatus status) {

    public static MasterResponse from(Brand b) {
        return new MasterResponse(b.getId(), b.getCode(), b.getName(), b.getDescription(),
                null, null, null, b.getStatus());
    }

    public static MasterResponse from(Collection c) {
        return new MasterResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                null, null, null, c.getStatus());
    }

    public static MasterResponse from(ProductCategory c) {
        return new MasterResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                c.getParent() == null ? null : c.getParent().getId(), null,
                c.getDisplayOrder(), c.getStatus());
    }

    public static MasterResponse from(ProductType t) {
        return new MasterResponse(t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getCategory() == null ? null : t.getCategory().getId(), t.isSizeable(),
                null, t.getStatus());
    }
}
