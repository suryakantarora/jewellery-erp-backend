package com.finotech.jewellery.modules.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.product.api.request.DesignRequest;
import com.finotech.jewellery.modules.product.api.request.LinkImageRequest;
import com.finotech.jewellery.modules.product.api.request.ProductRequest;
import com.finotech.jewellery.modules.product.api.request.ProductTypeRequest;
import com.finotech.jewellery.modules.product.api.response.ImageResponse;
import com.finotech.jewellery.modules.product.application.service.ProductMasterService;
import com.finotech.jewellery.modules.product.application.service.ProductService;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Designs had no pictures and products had a mapped table nobody could reach.
 * Both follow the item-image rules: the first picture is primary, a new primary
 * demotes the old, and removing the primary promotes the next.
 */
class CatalogueImageIntegrationTest extends IntegrationTestBase {

    @Autowired private ProductService productService;
    @Autowired private ProductMasterService productMasterService;

    private UUID designId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAsSuperAdmin();
        String unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var type = productMasterService.createProductType(new ProductTypeRequest(
                "PT" + unique, "Ring", null, true, null));
        designId = productService.createDesign(new DesignRequest(
                "DSG" + unique, "Lotus Band", type.id(), null, null, null, null, null)).id();
        productId = productService.createProduct(new ProductRequest(
                "SKU" + unique, "Lotus Ring", designId, type.id(), null, null, null,
                null, null, new BigDecimal("10.000"), null, null, null, null, null)).id();
    }

    @AfterEach
    void clear() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("the first design image becomes primary and shows on the design")
    void firstDesignImageIsPrimary() {
        ImageResponse first = productService.addDesignImage(designId,
                new LinkImageRequest("designs/a.jpg", "a.jpg", "image/jpeg", 10L, false, 1));

        assertThat(first.primaryImage()).isTrue();
        assertThat(productService.getDesign(designId).primaryImageKey()).isEqualTo("designs/a.jpg");
    }

    @Test
    @DisplayName("a new primary design image demotes the previous one")
    void newPrimaryDemotesOld() {
        productService.addDesignImage(designId,
                new LinkImageRequest("designs/a.jpg", "a.jpg", "image/jpeg", 1L, true, 1));
        productService.addDesignImage(designId,
                new LinkImageRequest("designs/b.jpg", "b.jpg", "image/jpeg", 1L, true, 2));

        List<ImageResponse> images = productService.designImages(designId);
        assertThat(images).hasSize(2);
        assertThat(images).filteredOn(ImageResponse::primaryImage)
                .extracting(ImageResponse::storageKey)
                .containsExactly("designs/b.jpg");
        assertThat(productService.getDesign(designId).primaryImageKey()).isEqualTo("designs/b.jpg");
    }

    @Test
    @DisplayName("removing the primary design image promotes the next one")
    void removingPrimaryPromotesNext() {
        ImageResponse primary = productService.addDesignImage(designId,
                new LinkImageRequest("designs/a.jpg", "a.jpg", "image/jpeg", 1L, true, 1));
        productService.addDesignImage(designId,
                new LinkImageRequest("designs/b.jpg", "b.jpg", "image/jpeg", 1L, false, 2));

        productService.removeDesignImage(designId, primary.id());

        assertThat(productService.designImages(designId))
                .extracting(ImageResponse::storageKey, ImageResponse::primaryImage)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("designs/b.jpg", true));
        assertThat(productService.getDesign(designId).primaryImageKey()).isEqualTo("designs/b.jpg");
    }

    @Test
    @DisplayName("a design image cannot be removed through another design")
    void cannotRemoveAnotherDesignsImage() {
        String unique = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID otherDesign = productService.createDesign(new DesignRequest(
                "DSG" + unique, "Other", null, null, null, null, null, null)).id();
        ImageResponse image = productService.addDesignImage(otherDesign,
                new LinkImageRequest("designs/x.jpg", "x.jpg", "image/jpeg", 1L, true, 1));

        assertThatThrownBy(() -> productService.removeDesignImage(designId, image.id()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("product images follow the same rules and surface on the product")
    void productImagesWork() {
        productService.addProductImage(productId,
                new LinkImageRequest("products/a.jpg", "a.jpg", "image/jpeg", 1L, false, 1));
        ImageResponse second = productService.addProductImage(productId,
                new LinkImageRequest("products/b.jpg", "b.jpg", "image/jpeg", 1L, true, 2));

        assertThat(productService.getProduct(productId).primaryImageKey()).isEqualTo("products/b.jpg");

        productService.removeProductImage(productId, second.id());

        assertThat(productService.getProduct(productId).primaryImageKey()).isEqualTo("products/a.jpg");
        assertThat(productService.productImages(productId)).hasSize(1);
    }
}
