// Product detail reads and admin create, update and delete.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.dto.Measurements;
import com.iloveshopping.catalog.dto.ProductDetail;
import com.iloveshopping.catalog.dto.ProductImageResponse;
import com.iloveshopping.catalog.dto.ProductRequest;
import com.iloveshopping.catalog.dto.ProductVariant;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final StorageService storageService;

    public ProductService(ProductRepository productRepository, ProductImageRepository imageRepository,
                          CategoryService categoryService, BrandService brandService,
                          StorageService storageService) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.categoryService = categoryService;
        this.brandService = brandService;
        this.storageService = storageService;
    }

    /** Inactive products 404 exactly like missing ones, so a hidden product can't be probed for. */
    @Transactional(readOnly = true)
    public ProductDetail getDetail(UUID id) {
        return toDetail(productRepository.findByIdAndActiveTrue(id).orElseThrow(ProductService::notFound));
    }

    @Transactional
    public ProductDetail create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        return toDetail(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductDetail update(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(ProductService::notFound);
        apply(product, request);
        return toDetail(productRepository.saveAndFlush(product));
    }

    /**
     * Images and reviews go with the product through ON DELETE CASCADE. The image files are removed
     * only after the transaction commits: deleting them first and then rolling back would leave
     * rows pointing at files that no longer exist.
     */
    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findById(id).orElseThrow(ProductService::notFound);
        List<String> urls = imageRepository.findByProductIdOrderByPrimaryDescDisplayOrderAscIdAsc(id)
                .stream().map(ProductImage::getUrl).toList();
        productRepository.delete(product);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                urls.forEach(storageService::delete);
            }
        });
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name().strip());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setCategory(categoryService.require(request.categoryId()));
        product.setBrand(request.brandId() == null ? null : brandService.require(request.brandId()));
        product.setAttributes(request.attributes());
        product.setActive(request.active() == null || request.active());
        product.setWeightKg(request.weightKg());
        product.setWidthCm(request.widthCm());
        product.setHeightCm(request.heightCm());
        product.setDepthCm(request.depthCm());
        product.setWeightLbs(UnitConversion.kgToLbs(request.weightKg()));
        product.setWidthIn(UnitConversion.cmToInches(request.widthCm()));
        product.setHeightIn(UnitConversion.cmToInches(request.heightCm()));
        product.setDepthIn(UnitConversion.cmToInches(request.depthCm()));
    }

    private ProductDetail toDetail(Product p) {
        List<ProductImageResponse> images = imageRepository.findByProductIdOrderByPrimaryDescDisplayOrderAscIdAsc(p.getId())
                .stream().map(ProductImageService::toResponse).toList();
        return new ProductDetail(
                p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                p.getStockQuantity(), p.getStockQuantity() > 0, p.isActive(),
                CategoryService.toRef(p.getCategory()), categoryService.breadcrumb(p.getCategory()),
                BrandService.toResponse(p.getBrand()), images, p.getAttributes(),
                new Measurements(p.getWeightKg(), p.getWidthCm(), p.getHeightCm(), p.getDepthCm(),
                        p.getWeightLbs(), p.getWidthIn(), p.getHeightIn(), p.getDepthIn()),
                p.getAverageRating(), p.getReviewCount() == null ? 0 : p.getReviewCount(),
                p.getCreatedAt(), p.getUpdatedAt(), variants(p));
    }

    /**
     * Siblings in the product's variant group, this product included, so the page can mark the
     * current option. The seed writes attributes.variant_group and attributes.variant ({"Colour": "Blue"});
     * a product without a group has no variants.
     */
    private List<ProductVariant> variants(Product p) {
        Map<String, Object> attrs = p.getAttributes();
        if (attrs == null || !(attrs.get("variant_group") instanceof String group)) {
            return List.of();
        }
        return productRepository.findActiveVariants(group).stream()
                .filter(v -> v.getAttributes().get("variant") instanceof Map<?, ?>)
                .map(v -> new ProductVariant(v.getId(), castOptions(v.getAttributes().get("variant")), v.getPrice(),
                        v.getStockQuantity() > 0,
                        imageRepository.findByProductIdOrderByPrimaryDescDisplayOrderAscIdAsc(v.getId()).stream()
                                .findFirst().map(ProductImage::getUrl).orElse(null)))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castOptions(Object options) {
        return (Map<String, Object>) options;
    }

    static CatalogNotFoundException notFound() {
        return new CatalogNotFoundException("product_not_found");
    }
}
