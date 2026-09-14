package com.finotech.jewellery.modules.product.application.service;

import com.finotech.jewellery.modules.product.api.request.CategoryRequest;
import com.finotech.jewellery.modules.product.api.request.ProductTypeRequest;
import com.finotech.jewellery.modules.product.api.request.SimpleMasterRequest;
import com.finotech.jewellery.modules.product.api.request.SizeRequest;
import com.finotech.jewellery.modules.product.api.response.MasterResponse;
import com.finotech.jewellery.modules.product.api.response.SizeResponse;
import com.finotech.jewellery.modules.product.domain.entity.Brand;
import com.finotech.jewellery.modules.product.domain.entity.Collection;
import com.finotech.jewellery.modules.product.domain.entity.ProductCategory;
import com.finotech.jewellery.modules.product.domain.entity.ProductType;
import com.finotech.jewellery.modules.product.domain.entity.Size;
import com.finotech.jewellery.modules.product.infrastructure.repository.BrandRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.CollectionRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.ProductCategoryRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.ProductTypeRepository;
import com.finotech.jewellery.modules.product.infrastructure.repository.SizeRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finotech.jewellery.modules.organization.application.CompanyScope;

/**
 * Lookup master data: categories, product types, brands, collections and sizes.
 */
@Service
@RequiredArgsConstructor
public class ProductMasterService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductTypeRepository productTypeRepository;
    private final BrandRepository brandRepository;
    private final CollectionRepository collectionRepository;
    private final SizeRepository sizeRepository;
    private final AuditService auditService;
    private final CompanyScope companyScope;

    // ---------- category ----------

    @Transactional(readOnly = true)
    public List<MasterResponse> listCategories() {
        return categoryRepository.findAllInCompany(companyScope.currentOrNull()).stream()
                .map(MasterResponse::from).toList();
    }

    @Transactional
    public MasterResponse createCategory(CategoryRequest request) {
        UUID companyId = companyScope.resolveForCreate(request.companyId());
        requireUniqueCode(categoryRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, request.code()),
                "Category", request.code());
        ProductCategory category = new ProductCategory();
        category.setCompanyId(companyId);
        applyCategory(category, request, null);
        ProductCategory saved = categoryRepository.save(category);
        auditService.record("CATEGORY_CREATED", "ProductCategory", saved.getId(), null,
                MasterResponse.from(saved));
        return MasterResponse.from(saved);
    }

    @Transactional
    public MasterResponse updateCategory(UUID id, CategoryRequest request) {
        ProductCategory category = requireCategory(id);
        MasterResponse before = MasterResponse.from(category);
        applyCategory(category, request, id);
        MasterResponse after = MasterResponse.from(category);
        auditService.record("CATEGORY_UPDATED", "ProductCategory", id, before, after);
        return after;
    }

    // ---------- product type ----------

    @Transactional(readOnly = true)
    public List<MasterResponse> listProductTypes() {
        return productTypeRepository.findAllByOrderByNameAsc().stream()
                .map(MasterResponse::from).toList();
    }

    @Transactional
    public MasterResponse createProductType(ProductTypeRequest request) {
        requireUniqueCode(productTypeRepository.existsByCodeIgnoreCase(request.code()),
                "ProductType", request.code());
        ProductType type = new ProductType();
        applyProductType(type, request);
        ProductType saved = productTypeRepository.save(type);
        auditService.record("PRODUCT_TYPE_CREATED", "ProductType", saved.getId(), null,
                MasterResponse.from(saved));
        return MasterResponse.from(saved);
    }

    @Transactional
    public MasterResponse updateProductType(UUID id, ProductTypeRequest request) {
        ProductType type = productTypeRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("ProductType", id));
        MasterResponse before = MasterResponse.from(type);
        applyProductType(type, request);
        MasterResponse after = MasterResponse.from(type);
        auditService.record("PRODUCT_TYPE_UPDATED", "ProductType", id, before, after);
        return after;
    }

    // ---------- brand ----------

    @Transactional(readOnly = true)
    public List<MasterResponse> listBrands() {
        return brandRepository.findAllByOrderByNameAsc().stream().map(MasterResponse::from).toList();
    }

    @Transactional
    public MasterResponse createBrand(SimpleMasterRequest request) {
        requireUniqueCode(brandRepository.existsByCodeIgnoreCase(request.code()), "Brand", request.code());
        Brand brand = new Brand();
        brand.setCode(request.code().trim().toUpperCase());
        brand.setName(request.name().trim());
        brand.setDescription(request.description());
        Brand saved = brandRepository.save(brand);
        auditService.record("BRAND_CREATED", "Brand", saved.getId(), null, MasterResponse.from(saved));
        return MasterResponse.from(saved);
    }

    // ---------- collection ----------

    @Transactional(readOnly = true)
    public List<MasterResponse> listCollections() {
        return collectionRepository.findAllByOrderByNameAsc().stream().map(MasterResponse::from).toList();
    }

    @Transactional
    public MasterResponse createCollection(SimpleMasterRequest request) {
        requireUniqueCode(collectionRepository.existsByCodeIgnoreCase(request.code()),
                "Collection", request.code());
        Collection collection = new Collection();
        collection.setCode(request.code().trim().toUpperCase());
        collection.setName(request.name().trim());
        collection.setDescription(request.description());
        Collection saved = collectionRepository.save(collection);
        auditService.record("COLLECTION_CREATED", "Collection", saved.getId(), null,
                MasterResponse.from(saved));
        return MasterResponse.from(saved);
    }

    // ---------- size ----------

    @Transactional(readOnly = true)
    public List<SizeResponse> listSizes(UUID productTypeId) {
        return sizeRepository.findAllByProductTypeIdOrderByDisplayOrderAscCodeAsc(productTypeId)
                .stream().map(SizeResponse::from).toList();
    }

    @Transactional
    public SizeResponse createSize(SizeRequest request) {
        ProductType type = productTypeRepository.findById(request.productTypeId())
                .orElseThrow(() -> NotFoundException.of("ProductType", request.productTypeId()));
        if (!type.isSizeable()) {
            throw new ValidationException("Product type " + type.getCode() + " is not sizeable");
        }
        if (sizeRepository.existsByProductTypeIdAndCodeIgnoreCase(type.getId(), request.code())) {
            throw new ConflictException("Size already exists for this product type: " + request.code());
        }
        Size size = new Size();
        size.setProductType(type);
        size.setCode(request.code().trim());
        size.setLabel(request.label().trim());
        size.setStandard(request.standard());
        size.setDisplayOrder(request.displayOrder());
        return SizeResponse.from(sizeRepository.save(size));
    }

    // ---------- helpers ----------

    private void applyCategory(ProductCategory category, CategoryRequest request, UUID selfId) {
        category.setCode(request.code().trim().toUpperCase());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setDisplayOrder(request.displayOrder());
        if (request.parentId() != null) {
            if (request.parentId().equals(selfId)) {
                throw new ValidationException("A category cannot be its own parent");
            }
            ProductCategory parent = requireCategory(request.parentId());
            if (!parent.getCompanyId().equals(category.getCompanyId())) {
                throw NotFoundException.of("ProductCategory", request.parentId());
            }
            category.setParent(parent);
        } else {
            category.setParent(null);
        }
    }

    private void applyProductType(ProductType type, ProductTypeRequest request) {
        type.setCode(request.code().trim().toUpperCase());
        type.setName(request.name().trim());
        type.setSizeable(request.sizeable());
        type.setDescription(request.description());
        type.setCategory(request.categoryId() == null ? null : requireCategory(request.categoryId()));
    }

    private ProductCategory requireCategory(UUID id) {
        return categoryRepository.findByIdInCompany(id, companyScope.currentOrNull())
                .orElseThrow(() -> NotFoundException.of("ProductCategory", id));
    }

    private void requireUniqueCode(boolean exists, String entity, String code) {
        if (exists) {
            throw new ConflictException(entity + " code already exists: " + code);
        }
    }
}
