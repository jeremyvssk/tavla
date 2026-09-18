// Product endpoints: public search/browse and detail, admin create, replace and delete.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.dto.ProductDetail;
import com.iloveshopping.catalog.dto.ProductRequest;
import com.iloveshopping.catalog.dto.ProductSearchParams;
import com.iloveshopping.catalog.dto.ProductSearchResponse;
import com.iloveshopping.catalog.search.SearchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final SearchService searchService;
    private final ProductService productService;

    public ProductController(SearchService searchService, ProductService productService) {
        this.searchService = searchService;
        this.productService = productService;
    }

    /** Browsing a category and searching are the same request: ?category=chess with or without ?q=. */
    @GetMapping
    public ProductSearchResponse search(@Valid @ModelAttribute ProductSearchParams params) {
        return searchService.search(params);
    }

    @GetMapping("/{id}")
    public ProductDetail detail(@PathVariable UUID id) {
        return productService.getDetail(id);
    }

    @PostMapping
    public ResponseEntity<ProductDetail> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    public ProductDetail update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
