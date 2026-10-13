// The staff's catalog API: the product list with every state, one product for the edit page, bulk changes, the CSV export and the category tree.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.dto.BulkProductRequest;
import com.iloveshopping.catalog.dto.BulkProductResponse;
import com.iloveshopping.catalog.dto.ProductDetail;
import com.iloveshopping.catalog.dto.StaffCategoryNode;
import com.iloveshopping.catalog.dto.StaffProductPageResponse;
import com.iloveshopping.catalog.dto.StaffProductParams;
import com.iloveshopping.csv.Csv;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Sales and admins only (SecurityConfig, StaffArea.CATALOG). Writes to a single product stay on /products. */
@RestController
@RequestMapping("/staff")
public class StaffCatalogController {

    private final StaffProductService staffProductService;
    private final ProductService productService;
    private final CategoryService categoryService;

    public StaffCatalogController(StaffProductService staffProductService, ProductService productService,
                                  CategoryService categoryService) {
        this.staffProductService = staffProductService;
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping("/products")
    public StaffProductPageResponse products(@Valid @ModelAttribute StaffProductParams params) {
        return staffProductService.list(params);
    }

    @GetMapping("/products/{id}")
    public ProductDetail product(@PathVariable UUID id) {
        return productService.getStaffDetail(id);
    }

    @PostMapping("/products/bulk")
    public BulkProductResponse bulk(@Valid @RequestBody BulkProductRequest request) {
        return staffProductService.bulk(request);
    }

    @GetMapping("/products/export")
    public ResponseEntity<byte[]> export(@Valid @ModelAttribute StaffProductParams params) {
        return Csv.download(staffProductService.export(params), "tavla-products.csv");
    }

    @GetMapping("/categories")
    public List<StaffCategoryNode> categories() {
        return categoryService.staffTree();
    }
}
