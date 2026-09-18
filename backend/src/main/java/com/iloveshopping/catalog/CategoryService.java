// Category tree reads, breadcrumbs, and admin creation.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.dto.CategoryNode;
import com.iloveshopping.catalog.dto.CategoryRef;
import com.iloveshopping.catalog.dto.CategoryRequest;
import com.iloveshopping.catalog.exception.CatalogConflictException;
import com.iloveshopping.catalog.exception.InvalidReferenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * The whole active tree in one query, assembled in memory. A category table stays small, so
     * this beats a recursive query per level. A child of an inactive parent is hidden with it.
     */
    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<Category> all = categoryRepository.findByActiveTrueOrderByNameAsc();
        Map<Integer, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        return children(null, byParent);
    }

    private List<CategoryNode> children(Integer parentId, Map<Integer, List<Category>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> new CategoryNode(c.getId(), c.getName(), c.getSlug(), children(c.getId(), byParent)))
                .toList();
    }

    /** Root-first path to a category, for breadcrumbs on the product page. */
    @Transactional(readOnly = true)
    public List<CategoryRef> breadcrumb(Category category) {
        List<CategoryRef> path = new ArrayList<>();
        Category current = category;
        // Depth guard: parents are only set at creation, so a cycle can't form, but a loop that
        // trusts that forever is one bad manual UPDATE away from hanging a request thread.
        for (int depth = 0; current != null && depth < 10; depth++) {
            path.add(toRef(current));
            current = current.getParentId() == null ? null
                    : categoryRepository.findById(current.getParentId()).orElse(null);
        }
        Collections.reverse(path);
        return path;
    }

    @Transactional
    public CategoryRef create(CategoryRequest request) {
        if (request.parentId() != null && !categoryRepository.existsById(request.parentId())) {
            throw new InvalidReferenceException("parentId");
        }
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new CatalogConflictException("slug_already_exists");
        }
        Category category = new Category();
        category.setName(request.name().strip());
        category.setSlug(request.slug());
        category.setParentId(request.parentId());
        try {
            return toRef(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException e) {
            // Lost a race on the slug, or a sibling already has this name (uq_categories_parent_name).
            throw new CatalogConflictException("category_already_exists");
        }
    }

    Category require(Integer id) {
        return categoryRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new InvalidReferenceException("categoryId"));
    }

    static CategoryRef toRef(Category c) {
        return new CategoryRef(c.getId(), c.getName(), c.getSlug());
    }
}
