package com.finotech.jewellery.modules.product.application.service;

import com.finotech.jewellery.modules.product.api.request.DesignRequest;
import com.finotech.jewellery.modules.product.api.request.LinkImageRequest;
import com.finotech.jewellery.modules.product.api.request.ProductRequest;
import com.finotech.jewellery.modules.product.api.response.DesignResponse;
import com.finotech.jewellery.modules.product.api.response.ImageResponse;
import com.finotech.jewellery.modules.product.api.response.ProductResponse;
import com.finotech.jewellery.modules.product.application.ProductCatalog;
import com.finotech.jewellery.modules.product.domain.entity.DesignImage;
import com.finotech.jewellery.modules.product.domain.entity.JewelleryDesign;
import com.finotech.jewellery.modules.product.domain.entity.Product;
import com.finotech.jewellery.modules.product.domain.entity.ProductImage;
import com.finotech.jewellery.modules.product.domain.enums.MasterStatus;
import com.finotech.jewellery.modules.product.infrastructure.repository.BrandRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.CollectionRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.DesignImageRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.JewelleryDesignRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.ProductCategoryRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.ProductImageRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.ProductRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.ProductTypeRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Designs and the reusable product master. Creating a product never creates
 * stock: physical items are made in the inventory module.
 */
@Service
@RequiredArgsConstructor
public class ProductService implements ProductCatalog {

    private final ProductRepository productRepository;
    private final JewelleryDesignRepository designRepository;
    private final ProductTypeRepository productTypeRepository;
    private final ProductCategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final CollectionRepository collectionRepository;
    private final DesignImageRepository designImageRepository;
    private final ProductImageRepository productImageRepository;
    private final AuditService auditService;

    // ---------- design ----------

    @Transactional(readOnly = true)
    public PageResponse<DesignResponse> searchDesigns(String search, UUID collectionId,
                                                      UUID productTypeId, Pageable pageable) {
        return PageResponse.of(designRepository.search(search, collectionId, productTypeId, pageable),
                DesignResponse::from);
    }

    @Transactional(readOnly = true)
    public DesignResponse getDesign(UUID id) {
        return DesignResponse.from(requireDesign(id));
    }

    @Transactional
    public DesignResponse createDesign(DesignRequest request) {
        if (designRepository.existsByDesignCodeIgnoreCase(request.designCode())) {
            throw new ConflictException("Design code already exists: " + request.designCode());
        }
        JewelleryDesign design = new JewelleryDesign();
        applyDesign(design, request);
        JewelleryDesign saved = designRepository.save(design);
        auditService.record("DESIGN_CREATED", "JewelleryDesign", saved.getId(), null,
                DesignResponse.from(saved));
        return DesignResponse.from(saved);
    }

    @Transactional
    public DesignResponse updateDesign(UUID id, DesignRequest request) {
        JewelleryDesign design = requireDesign(id);
        DesignResponse before = DesignResponse.from(design);
        applyDesign(design, request);
        DesignResponse after = DesignResponse.from(design);
        auditService.record("DESIGN_UPDATED", "JewelleryDesign", id, before, after);
        return after;
    }

    // ---------- product ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(String search, UUID categoryId,
                                                        UUID productTypeId, UUID brandId,
                                                        UUID collectionId, Pageable pageable) {
        return PageResponse.of(
                productRepository.search(search, categoryId, productTypeId, brandId, collectionId, pageable),
                ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        return ProductResponse.from(requireProductEntity(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new ConflictException("SKU already exists: " + request.sku());
        }
        Product product = new Product();
        applyProduct(product, request);
        Product saved = productRepository.save(product);
        auditService.record("PRODUCT_CREATED", "Product", saved.getId(), null,
                ProductResponse.from(saved));
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = requireProductEntity(id);
        ProductResponse before = ProductResponse.from(product);
        if (!product.getSku().equalsIgnoreCase(request.sku())
                && productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new ConflictException("SKU already exists: " + request.sku());
        }
        applyProduct(product, request);
        ProductResponse after = ProductResponse.from(product);
        auditService.record("PRODUCT_UPDATED", "Product", id, before, after);
        return after;
    }

    @Transactional
    public void deactivateProduct(UUID id) {
        Product product = requireProductEntity(id);
        product.setStatus(MasterStatus.INACTIVE);
        auditService.record("PRODUCT_DEACTIVATED", "Product", id, null, null);
    }

    // ---------- design images ----------

    @Transactional(readOnly = true)
    public List<ImageResponse> designImages(UUID designId) {
        requireDesign(designId);
        return designImageRepository.findAllByDesignIdOrderByDisplayOrderAscCreatedAtAsc(designId)
                .stream().map(ImageResponse::from).toList();
    }

    /**
     * Links an already-uploaded file to the design. A new primary demotes the
     * previous one, and the first picture is primary whether or not it says so
     * — the same rules as item images, for the same reasons.
     */
    @Transactional
    public ImageResponse addDesignImage(UUID designId, LinkImageRequest request) {
        JewelleryDesign design = requireDesign(designId);

        if (request.primaryImage()) {
            designImageRepository.findAllByDesignIdAndPrimaryImageTrue(designId)
                    .forEach(existing -> existing.setPrimaryImage(false));
            designImageRepository.flush();
        }

        DesignImage image = new DesignImage();
        image.setDesign(design);
        image.setStorageKey(request.storageKey().trim());
        image.setFileName(request.fileName());
        image.setContentType(request.contentType());
        image.setSizeBytes(request.sizeBytes());
        image.setPrimaryImage(request.primaryImage()
                || designImageRepository.findAllByDesignIdOrderByDisplayOrderAscCreatedAtAsc(designId)
                        .isEmpty());
        image.setDisplayOrder(request.displayOrder());

        DesignImage saved = designImageRepository.saveAndFlush(image);
        design.getImages().add(saved);
        auditService.record("DESIGN_IMAGE_ADDED", "JewelleryDesign", designId, null,
                Map.of("storageKey", saved.getStorageKey()));
        return ImageResponse.from(saved);
    }

    /** Unlinks an image; the stored object is kept, as other references may share it. */
    @Transactional
    public void removeDesignImage(UUID designId, UUID imageId) {
        JewelleryDesign design = requireDesign(designId);
        DesignImage image = designImageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image not found"));
        if (!image.getDesign().getId().equals(designId)) {
            throw new ValidationException("That image does not belong to this design");
        }
        boolean wasPrimary = image.isPrimaryImage();
        design.getImages().remove(image);
        designImageRepository.delete(image);
        designImageRepository.flush();

        if (wasPrimary) {
            designImageRepository.findAllByDesignIdOrderByDisplayOrderAscCreatedAtAsc(designId)
                    .stream().findFirst().ifPresent(next -> next.setPrimaryImage(true));
        }
        auditService.record("DESIGN_IMAGE_REMOVED", "JewelleryDesign", designId, null, null);
    }

    // ---------- product images ----------

    @Transactional(readOnly = true)
    public List<ImageResponse> productImages(UUID productId) {
        requireProductEntity(productId);
        return productImageRepository.findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(productId)
                .stream().map(ImageResponse::from).toList();
    }

    @Transactional
    public ImageResponse addProductImage(UUID productId, LinkImageRequest request) {
        Product product = requireProductEntity(productId);

        if (request.primaryImage()) {
            productImageRepository.findAllByProductIdAndPrimaryImageTrue(productId)
                    .forEach(existing -> existing.setPrimaryImage(false));
            productImageRepository.flush();
        }

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setStorageKey(request.storageKey().trim());
        image.setFileName(request.fileName());
        image.setContentType(request.contentType());
        image.setSizeBytes(request.sizeBytes());
        image.setPrimaryImage(request.primaryImage()
                || productImageRepository.findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(productId)
                        .isEmpty());
        image.setDisplayOrder(request.displayOrder());

        ProductImage saved = productImageRepository.saveAndFlush(image);
        product.getImages().add(saved);
        auditService.record("PRODUCT_IMAGE_ADDED", "Product", productId, null,
                Map.of("storageKey", saved.getStorageKey()));
        return ImageResponse.from(saved);
    }

    @Transactional
    public void removeProductImage(UUID productId, UUID imageId) {
        Product product = requireProductEntity(productId);
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image not found"));
        if (!image.getProduct().getId().equals(productId)) {
            throw new ValidationException("That image does not belong to this product");
        }
        boolean wasPrimary = image.isPrimaryImage();
        product.getImages().remove(image);
        productImageRepository.delete(image);
        productImageRepository.flush();

        if (wasPrimary) {
            productImageRepository.findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(productId)
                    .stream().findFirst().ifPresent(next -> next.setPrimaryImage(true));
        }
        auditService.record("PRODUCT_IMAGE_REMOVED", "Product", productId, null, null);
    }

    // ---------- cross-module catalog ----------

    @Override
    @Transactional(readOnly = true)
    public ProductView requireProduct(UUID productId) {
        Product p = requireProductEntity(productId);
        return new ProductView(p.getId(), p.getSku(), p.getName(),
                p.getDesign() == null ? null : p.getDesign().getId(), p.getProductType().getId(),
                p.getDefaultMetalId(), p.getDefaultPurityId(), p.getDefaultMakingChargeValue(),
                p.getDefaultMakingChargeType(), p.getDefaultWastagePercentage(),
                p.getStatus() == MasterStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ProductLabel> labelsFor(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId,
                        p -> new ProductLabel(p.getId(), p.getSku(), p.getName())));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> designNamesFor(Collection<UUID> designIds) {
        if (designIds == null || designIds.isEmpty()) {
            return Map.of();
        }
        return designRepository.findAllById(designIds).stream()
                .collect(Collectors.toMap(JewelleryDesign::getId, JewelleryDesign::getName));
    }

    // ---------- helpers ----------

    private Product requireProductEntity(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> NotFoundException.of("Product", id));
    }

    private JewelleryDesign requireDesign(UUID id) {
        return designRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("JewelleryDesign", id));
    }

    private void applyDesign(JewelleryDesign design, DesignRequest request) {
        design.setDesignCode(request.designCode().trim().toUpperCase());
        design.setName(request.name().trim());
        design.setDesigner(request.designer());
        design.setNominalGrossWeight(request.nominalGrossWeight());
        design.setDescription(request.description());
        design.setProductType(request.productTypeId() == null ? null
                : productTypeRepository.findById(request.productTypeId())
                        .orElseThrow(() -> NotFoundException.of("ProductType", request.productTypeId())));
        design.setCollection(request.collectionId() == null ? null
                : collectionRepository.findById(request.collectionId())
                        .orElseThrow(() -> NotFoundException.of("Collection", request.collectionId())));
        design.setBrand(request.brandId() == null ? null
                : brandRepository.findById(request.brandId())
                        .orElseThrow(() -> NotFoundException.of("Brand", request.brandId())));
    }

    private void applyProduct(Product product, ProductRequest request) {
        product.setSku(request.sku().trim().toUpperCase());
        product.setName(request.name().trim());
        product.setNominalGrossWeight(request.nominalGrossWeight());
        product.setDefaultMetalId(request.defaultMetalId());
        product.setDefaultPurityId(request.defaultPurityId());
        product.setDefaultMakingChargeType(request.defaultMakingChargeType());
        product.setDefaultMakingChargeValue(request.defaultMakingChargeValue());
        product.setDefaultWastagePercentage(request.defaultWastagePercentage());
        product.setHsnCode(request.hsnCode());
        product.setDescription(request.description());

        product.setProductType(productTypeRepository.findById(request.productTypeId())
                .orElseThrow(() -> NotFoundException.of("ProductType", request.productTypeId())));
        product.setDesign(request.designId() == null ? null : requireDesign(request.designId()));
        product.setCategory(request.categoryId() == null ? null
                : categoryRepository.findById(request.categoryId())
                        .orElseThrow(() -> NotFoundException.of("ProductCategory", request.categoryId())));
        product.setBrand(request.brandId() == null ? null
                : brandRepository.findById(request.brandId())
                        .orElseThrow(() -> NotFoundException.of("Brand", request.brandId())));
        product.setCollection(request.collectionId() == null ? null
                : collectionRepository.findById(request.collectionId())
                        .orElseThrow(() -> NotFoundException.of("Collection", request.collectionId())));
    }
}
