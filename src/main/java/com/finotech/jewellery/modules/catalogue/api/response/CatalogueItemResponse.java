package com.finotech.jewellery.modules.catalogue.api.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One piece as it should be shown to a customer.
 *
 * <p>Two things are deliberate about this shape.
 *
 * <p><b>Names, not ids.</b> Every other item response returns bare UUIDs and
 * leaves the client to resolve them. A storefront has no reference cache to
 * resolve them with, and neither does a salesperson holding the phone out
 * across the counter, so the join happens once on the server.
 *
 * <p><b>No cost, anywhere.</b> There is no {@code purchaseCost},
 * {@code makingCost}, {@code totalCost}, supplier, location or bin — not hidden
 * by a permission, simply absent. The whole point of a separate response is
 * that it cannot leak what a customer must never see, whichever client asks and
 * however the caller is authenticated. That is what makes this shape safe to
 * serve publicly later.
 */
public record CatalogueItemResponse(UUID id,
                                    String itemCode,
                                    String productName,
                                    String categoryName,
                                    String typeName,
                                    String metalName,
                                    String purityCode,
                                    String purityName,
                                    BigDecimal grossWeight,
                                    BigDecimal netMetalWeight,
                                    int stoneCount,
                                    BigDecimal totalCarat,
                                    String hallmarkNumber,
                                    String primaryImageKey,
                                    /** From the pricing engine. Null if it could not be priced. */
                                    BigDecimal price,
                                    String currency) {
}
