package com.finotech.jewellery.modules.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import com.finotech.jewellery.modules.catalogue.api.response.CatalogueItemResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The catalogue is the one response shown to people outside the business, and
 * it is written to become a public storefront endpoint.
 *
 * <p>Its safety rests on a structural property rather than on a permission:
 * there is no cost, supplier or location field to leak, whoever asks. A field
 * added later out of convenience would quietly destroy that, and no runtime
 * test would notice — the value would simply start appearing. So the shape
 * itself is asserted.
 */
class CatalogueResponseShapeTest {

    /** Substrings that must never appear in a customer-facing field name. */
    private static final List<String> FORBIDDEN = List.of(
            "cost", "supplier", "purchase", "margin", "profit",
            "location", "bin", "branch", "reserved", "owner", "notes");

    /**
     * The forbidden list is the test's whole value, so it is worth keeping
     * honest: these are the field names that would matter if one appeared.
     */
    @Test
    @DisplayName("the guard list still matches the internal fields that exist")
    void guardListCoversWhatInventoryActuallyReturns() {
        List<String> internalOnInventory = Arrays.stream(
                        com.finotech.jewellery.modules.inventory.api.response
                                .JewelleryItemResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(n -> n.toLowerCase(Locale.ROOT))
                .filter(n -> FORBIDDEN.stream().anyMatch(n::contains))
                .toList();

        assertThat(internalOnInventory)
                .as("the staff response should carry internal fields; "
                        + "if it stops, this guard has quietly become vacuous")
                .isNotEmpty();
    }

    @Test
    @DisplayName("no internal or commercially sensitive field can reach a customer")
    void catalogueCarriesNothingInternal() {
        List<String> names = Arrays.stream(CatalogueItemResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(names)
                .as("sanity: the response should describe a piece")
                .contains("productName", "price", "currency");

        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            assertThat(FORBIDDEN.stream().anyMatch(lower::contains))
                    .as("'%s' must not be exposed to a customer", name)
                    .isFalse();
        }
    }
}
