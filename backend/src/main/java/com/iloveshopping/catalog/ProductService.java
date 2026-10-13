// Product detail reads, the cart's product lookups, reserving stock for orders, and staff create, update and delete.
package com.iloveshopping.catalog;

import com.iloveshopping.audit.AuditEvent;
import com.iloveshopping.audit.AuditKind;
import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.audit.AuditTarget;
import com.iloveshopping.catalog.dto.Measurements;
import com.iloveshopping.catalog.dto.ProductDetail;
import com.iloveshopping.catalog.dto.ProductForCart;
import com.iloveshopping.catalog.dto.ProductImageResponse;
import com.iloveshopping.catalog.dto.ProductRequest;
import com.iloveshopping.catalog.dto.ProductVariant;
import com.iloveshopping.catalog.exception.CatalogConflictException;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.storage.StorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final StorageService storageService;
    private final ProductUsage productUsage;
    private final AuditLog auditLog;

    public ProductService(ProductRepository productRepository, ProductImageRepository imageRepository,
                          CategoryService categoryService, BrandService brandService,
                          StorageService storageService, ProductUsage productUsage, AuditLog auditLog) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.categoryService = categoryService;
        this.brandService = brandService;
        this.storageService = storageService;
        this.productUsage = productUsage;
        this.auditLog = auditLog;
    }

    /** Inactive products 404 exactly like missing ones, so a hidden product can't be probed for. */
    @Transactional(readOnly = true)
    public ProductDetail getDetail(UUID id) {
        return toDetail(productRepository.findByIdAndActiveTrue(id).orElseThrow(ProductService::notFound));
    }

    /** For the staff's edit page: hidden and archived products too. */
    @Transactional(readOnly = true)
    public ProductDetail getStaffDetail(UUID id) {
        return toDetail(productRepository.findById(id).orElseThrow(ProductService::notFound));
    }

    /**
     * The catalog side of a cart: one query for the products and their brands, one for their photos.
     * Ids that match no product are simply absent from the map.
     */
    @Transactional(readOnly = true)
    public Map<UUID, ProductForCart> forCart(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProductImage> firstImage = new HashMap<>();
        imageRepository.findByProductIdInOrderByPrimaryDescDisplayOrderAscIdAsc(ids)
                .forEach(i -> firstImage.putIfAbsent(i.getProductId(), i));
        Map<UUID, ProductForCart> products = new HashMap<>();
        for (Product p : productRepository.findWithBrandByIdIn(ids)) {
            ProductImage image = firstImage.get(p.getId());
            products.put(p.getId(), new ProductForCart(p.getId(), p.getName(), p.getPrice(), p.getStockQuantity(),
                    p.isActive(), p.getBrand() == null ? null : p.getBrand().getName(), optionsLine(p),
                    image == null ? null : image.getUrl(), image == null ? null : image.getFraming()));
        }
        return products;
    }

    /**
     * Reserves stock for an order line: true when it was taken, false when not enough is left (or the
     * product stopped selling). Inside the order's transaction, so a later line failing puts this one
     * back too. Callers take lines in product-id order, so two orders can't deadlock on each other's rows.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserveStock(UUID productId, int quantity) {
        return productRepository.reserveStock(productId, quantity) == 1;
    }

    /** Puts an order line's stock back: its hold ran out, or the order was withdrawn or cancelled. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseStock(UUID productId, int quantity) {
        productRepository.releaseStock(productId, quantity);
    }

    /** The stock as committed right now; 0 for a product that no longer exists. */
    @Transactional(readOnly = true)
    public int currentStock(UUID productId) {
        Integer stock = productRepository.currentStock(productId);
        return stock == null ? 0 : stock;
    }

    @Transactional
    public ProductDetail create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        Product saved = saveChecked(product);
        auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Added {target}").target(AuditTarget.PRODUCT, saved.getId(), saved.getName()));
        return toDetail(saved);
    }

    /** A price moved further than PriceGuard allows Sales is refused before anything is written. */
    @Transactional
    public ProductDetail update(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(ProductService::notFound);
        PriceGuard.check(product.getPrice(), request.price());
        BigDecimal priceBefore = product.getPrice();
        Map<String, Object> before = snapshot(product);
        apply(product, request);
        Product saved = saveChecked(product);
        List<String> changed = new ArrayList<>();
        snapshot(saved).forEach((field, value) -> {
            if (!Objects.equals(before.get(field), value)) {
                changed.add(field);
            }
        });
        if (changed.remove("price")) {
            AuditEvent event = AuditEvent.of(AuditKind.PRODUCTS, "Changed the price of {target}")
                    .target(AuditTarget.PRODUCT, saved.getId(), saved.getName()).change(euros(priceBefore), euros(saved.getPrice()));
            auditLog.record(changed.isEmpty() ? event : event.detail("Also changed: " + String.join(", ", changed)));
        } else if (!changed.isEmpty()) {
            auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Edited {target}")
                    .target(AuditTarget.PRODUCT, saved.getId(), saved.getName()).detail("Changed: " + String.join(", ", changed)));
        }
        return toDetail(saved);
    }

    /**
     * For the bulk upload, which checks the whole file, guards prices and writes one audit entry for the
     * file itself. Creates a product when {@code id} is null. Not flushed: the import flushes once at the end.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID saveFromImport(UUID id, ProductRequest request) {
        Product product = id == null ? new Product() : productRepository.findById(id).orElseThrow(ProductService::notFound);
        apply(product, request);
        return productRepository.save(product).getId();
    }

    /**
     * Archived when any order points at it, so the order keeps its link and the product its history;
     * deleted otherwise. The answer says which.
     */
    @Transactional
    public boolean delete(UUID id) {
        Product product = productRepository.findById(id).orElseThrow(ProductService::notFound);
        if (!productUsage.ordered(List.of(id)).isEmpty()) {
            if (product.getArchivedAt() == null) {
                product.setActive(false);
                product.setArchivedAt(Instant.now());
                productRepository.save(product);
                auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Archived {target}")
                        .target(AuditTarget.PRODUCT, id, product.getName()).detail("In past orders, so kept"));
            }
            return true;
        }
        hardDelete(product);
        auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Deleted {target}").target(AuditTarget.PRODUCT, id, product.getName()));
        return false;
    }

    /**
     * Images and reviews go with the product through ON DELETE CASCADE. The image files are removed
     * only after the transaction commits: deleting them first and then rolling back would leave
     * rows pointing at files that no longer exist.
     */
    private void hardDelete(Product product) {
        UUID id = product.getId();
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

    /** Saves now, so a SKU another product holds is a 409 here and not a 500 at commit. */
    private Product saveChecked(Product product) {
        if (product.getSku() != null && productRepository.skuTakenByOther(product.getSku(), product.getId())) {
            throw new CatalogConflictException("sku_already_exists");
        }
        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            // lost a race on the SKU: someone saved it between the check and this write
            throw new CatalogConflictException("sku_already_exists");
        }
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name().strip());
        product.setSku(request.sku() == null || request.sku().isBlank() ? null : request.sku().strip());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setCategory(categoryService.require(request.categoryId()));
        product.setBrand(request.brandId() == null ? null : brandService.require(request.brandId()));
        product.setAttributes(request.attributes());
        product.setActive(request.active() == null || request.active());
        if (product.isActive()) {
            // showing an archived product again brings it back into the everyday list
            product.setArchivedAt(null);
        }
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
                p.getId(), p.getName(), p.getSku(), p.getDescription(), p.getPrice(),
                p.getStockQuantity(), p.getStockQuantity() > 0, p.isActive(), p.getArchivedAt() != null,
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

    /** What an edit is compared on for the audit log; the field names are the ones the edit page shows. */
    private static Map<String, Object> snapshot(Product p) {
        Map<String, Object> s = new HashMap<>();
        s.put("name", p.getName());
        s.put("SKU", p.getSku());
        s.put("description", p.getDescription());
        s.put("price", p.getPrice() == null ? null : p.getPrice().stripTrailingZeros());
        s.put("stock", p.getStockQuantity());
        s.put("category", p.getCategory() == null ? null : p.getCategory().getId());
        s.put("brand", p.getBrand() == null ? null : p.getBrand().getId());
        s.put("details", p.getAttributes());
        s.put("shown in the shop", p.isActive());
        s.put("weight", p.getWeightKg() == null ? null : p.getWeightKg().stripTrailingZeros());
        s.put("size", List.of(String.valueOf(p.getWidthCm()), String.valueOf(p.getHeightCm()), String.valueOf(p.getDepthCm())));
        return s;
    }

    static String euros(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** "Colour: Green, Size: Large", so two variants of one product can be told apart in a cart. */
    private static String optionsLine(Product p) {
        Map<String, Object> attrs = p.getAttributes();
        if (attrs == null || !(attrs.get("variant") instanceof Map<?, ?> options) || options.isEmpty()) {
            return null;
        }
        return options.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", "));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castOptions(Object options) {
        return (Map<String, Object>) options;
    }

    static CatalogNotFoundException notFound() {
        return new CatalogNotFoundException("product_not_found");
    }
}
