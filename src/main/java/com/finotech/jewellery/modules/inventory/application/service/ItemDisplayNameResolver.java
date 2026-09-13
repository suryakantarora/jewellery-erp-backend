package com.finotech.jewellery.modules.inventory.application.service;

import com.finotech.jewellery.modules.inventory.api.response.JewelleryItemResponse.DisplayNames;
import com.finotech.jewellery.modules.inventory.domain.entity.JewelleryItem;
import com.finotech.jewellery.modules.metal.application.MetalNames;
import com.finotech.jewellery.modules.organization.application.OrganizationNames;
import com.finotech.jewellery.modules.product.application.ProductCatalog;
import com.finotech.jewellery.modules.supplier.application.SupplierNames;
import com.finotech.jewellery.modules.warehouse.application.BinDirectory;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Labels the references on a batch of items — product, design, metal, purity,
 * location, branch, bin, supplier — with one lookup per reference type.
 *
 * <p>The mobile list screen needs "22K Gold Ring · Pakse · TRAY-3", not eight
 * UUIDs, and cannot afford eight round trips per row to get it. Resolving the
 * distinct ids of a whole page at once keeps a page of thirty rows at a
 * handful of queries regardless of its size.
 */
@Component
@RequiredArgsConstructor
public class ItemDisplayNameResolver {

    private final ProductCatalog productCatalog;
    private final OrganizationNames organizationNames;
    private final MetalNames metalNames;
    private final BinDirectory binDirectory;
    private final SupplierNames supplierNames;

    public DisplayNames resolve(Collection<JewelleryItem> items) {
        if (items == null || items.isEmpty()) {
            return DisplayNames.EMPTY;
        }
        return new DisplayNames(
                productCatalog.labelsFor(ids(items, JewelleryItem::getProductId)),
                productCatalog.designNamesFor(ids(items, JewelleryItem::getDesignId)),
                metalNames.metalNamesFor(ids(items, JewelleryItem::getMetalId)),
                metalNames.purityCodesFor(ids(items, JewelleryItem::getPurityId)),
                organizationNames.locationNamesFor(ids(items, JewelleryItem::getCurrentLocationId)),
                organizationNames.branchNamesFor(ids(items, JewelleryItem::getCurrentBranchId)),
                binDirectory.codesFor(ids(items, JewelleryItem::getBinId)),
                supplierNames.namesFor(ids(items, JewelleryItem::getSupplierId)));
    }

    private static Set<UUID> ids(Collection<JewelleryItem> items, Function<JewelleryItem, UUID> ref) {
        return items.stream().map(ref).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
