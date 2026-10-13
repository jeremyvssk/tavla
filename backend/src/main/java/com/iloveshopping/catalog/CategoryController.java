// Category endpoints: public tree read; the staff's create, edit, move and delete.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.dto.CategoryMoveRequest;
import com.iloveshopping.catalog.dto.CategoryNode;
import com.iloveshopping.catalog.dto.CategoryRef;
import com.iloveshopping.catalog.dto.CategoryRequest;
import com.iloveshopping.catalog.dto.CategoryUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryNode> tree() {
        return categoryService.tree();
    }

    @PostMapping
    public ResponseEntity<CategoryRef> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public CategoryRef update(@PathVariable Integer id, @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.update(id, request);
    }

    @PutMapping("/{id}/position")
    public CategoryRef move(@PathVariable Integer id, @Valid @RequestBody CategoryMoveRequest request) {
        return categoryService.move(id, request);
    }

    /** 409 category_not_empty while products or shelves are in it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
