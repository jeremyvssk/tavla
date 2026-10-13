// Category tree reads, breadcrumbs, and the staff's create, edit, move and delete.
package com.iloveshopping.catalog;

import com.iloveshopping.audit.AuditEvent;
import com.iloveshopping.audit.AuditKind;
import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.audit.AuditTarget;
import com.iloveshopping.catalog.dto.CategoryMoveRequest;
import com.iloveshopping.catalog.dto.CategoryNode;
import com.iloveshopping.catalog.dto.CategoryRef;
import com.iloveshopping.catalog.dto.CategoryRequest;
import com.iloveshopping.catalog.dto.CategoryUpdateRequest;
import com.iloveshopping.catalog.dto.StaffCategoryNode;
import com.iloveshopping.catalog.exception.CatalogConflictException;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.catalog.exception.InvalidReferenceException;
import com.iloveshopping.exception.FieldValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class CategoryService {

    /** Shop, Chess, Sets, Wooden, Travel: deeper than this and the menu and breadcrumbs stop being usable. */
    static final int MAX_DEPTH = 6;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AuditLog auditLog;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository, AuditLog auditLog) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.auditLog = auditLog;
    }

    /**
     * The whole active tree in one query, assembled in memory. A category table stays small, so
     * this beats a recursive query per level. A child of an inactive parent is hidden with it.
     */
    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<Category> all = categoryRepository.findByActiveTrueOrderByPositionAscNameAsc();
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

    /** The staff's tree: every shelf, hidden ones too, in the shop's order, with its product count. */
    @Transactional(readOnly = true)
    public List<StaffCategoryNode> staffTree() {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : productRepository.countByCategory()) {
            counts.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        return staffChildren(null, byParent(categoryRepository.findAllByOrderByPositionAscNameAsc()), counts);
    }

    private List<StaffCategoryNode> staffChildren(Integer parentId, Map<Integer, List<Category>> byParent, Map<Integer, Long> counts) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> new StaffCategoryNode(c.getId(), c.getName(), c.getSlug(), c.getParentId(), Boolean.TRUE.equals(c.getActive()),
                        counts.getOrDefault(c.getId(), 0L), staffChildren(c.getId(), byParent, counts)))
                .toList();
    }

    /** The shelf with this slug and every shelf below it, hidden ones too; empty for a slug that matches none. */
    @Transactional(readOnly = true)
    public Set<Integer> subtreeIds(String slug) {
        List<Category> all = categoryRepository.findAll();
        return all.stream().filter(c -> c.getSlug().equals(slug)).findFirst()
                .map(root -> subtree(root.getId(), byParent(all))).orElse(Set.of());
    }

    /**
     * Rename, re-address, hide or show a shelf, or hang it under another. A shelf can't go under itself or
     * under one of its own shelves: that would cut it and everything below it off from the tree.
     */
    @Transactional
    public CategoryRef update(Integer id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow(CategoryService::notFound);
        if (categoryRepository.existsBySlugAndIdNot(request.slug(), id)) {
            throw new CatalogConflictException("slug_already_exists");
        }
        String before = category.getName();
        boolean wasActive = Boolean.TRUE.equals(category.getActive());
        if (!Objects.equals(category.getParentId(), request.parentId())) {
            checkNewParent(category, request.parentId());
            category.setParentId(request.parentId());
            category.setPosition(categoryRepository.nextPosition(request.parentId()));
        }
        category.setName(request.name().strip());
        category.setSlug(request.slug());
        category.setActive(request.active());
        Category saved = saveChecked(category);
        AuditEvent event = AuditEvent.of(AuditKind.PRODUCTS, "Edited the category {target}").target(AuditTarget.CATEGORY, id, saved.getName());
        if (!before.equals(saved.getName())) {
            event = event.change(before, saved.getName());
        } else if (wasActive != request.active()) {
            event = event.change(wasActive ? "Shown" : "Hidden", request.active() ? "Shown" : "Hidden");
        }
        auditLog.record(event);
        return toRef(saved);
    }

    /**
     * Puts the shelf under {@code parentId}, just before {@code beforeId} (last when null), and numbers that
     * shelf's children 0, 1, 2... again. Two people reordering the same shelf at once both succeed and the
     * later one's order stands; positions never collide in a way that loses a shelf, since ties sort by name.
     */
    @Transactional
    public CategoryRef move(Integer id, CategoryMoveRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow(CategoryService::notFound);
        Integer parentId = request.parentId();
        if (!Objects.equals(category.getParentId(), parentId)) {
            checkNewParent(category, parentId);
        }
        List<Category> siblings = new ArrayList<>(categoryRepository.findAllByOrderByPositionAscNameAsc().stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId) && !c.getId().equals(id)).toList());
        int at = siblings.size();
        if (request.beforeId() != null) {
            at = -1;
            for (int i = 0; i < siblings.size(); i++) {
                if (siblings.get(i).getId().equals(request.beforeId())) {
                    at = i;
                }
            }
            if (at < 0) {
                throw new FieldValidationException("beforeId", "is not a shelf under that parent");
            }
        }
        Integer fromParent = category.getParentId();
        siblings.add(at, category);
        category.setParentId(parentId);
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setPosition(i);
        }
        Category saved = saveChecked(category);
        categoryRepository.saveAll(siblings);
        AuditEvent event = AuditEvent.of(AuditKind.PRODUCTS, "Moved the category {target}").target(AuditTarget.CATEGORY, id, saved.getName());
        if (!Objects.equals(fromParent, parentId)) {
            event = event.change(parentName(fromParent), parentName(parentId));
        }
        auditLog.record(event);
        return toRef(saved);
    }

    /** Only an empty shelf goes: products, archived ones too, and shelves below must be moved off it first. */
    @Transactional
    public void delete(Integer id) {
        Category category = categoryRepository.findById(id).orElseThrow(CategoryService::notFound);
        if (categoryRepository.existsByParentId(id) || productRepository.existsByCategoryId(id)) {
            throw new CatalogConflictException("category_not_empty");
        }
        categoryRepository.delete(category);
        auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Deleted the category {target}").target(AuditTarget.CATEGORY, id, category.getName()));
    }

    /** Every shelf by slug, hidden ones too, for the bulk upload's category column. */
    @Transactional(readOnly = true)
    public Map<String, Category> bySlug() {
        Map<String, Category> all = new HashMap<>();
        categoryRepository.findAll().forEach(c -> all.put(c.getSlug(), c));
        return all;
    }

    private void checkNewParent(Category category, Integer parentId) {
        if (parentId == null) {
            return;
        }
        List<Category> all = categoryRepository.findAll();
        Map<Integer, List<Category>> byParent = byParent(all);
        Set<Integer> below = subtree(category.getId(), byParent);
        if (below.contains(parentId)) {
            throw new FieldValidationException("parentId", "can't be the category itself or a category below it");
        }
        Map<Integer, Category> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.getId(), c));
        if (!byId.containsKey(parentId)) {
            throw new InvalidReferenceException("parentId");
        }
        int depth = 0;
        for (Integer at = parentId; at != null && depth <= MAX_DEPTH; at = byId.get(at).getParentId()) {
            depth++;
        }
        if (depth + height(category.getId(), byParent) > MAX_DEPTH) {
            throw new FieldValidationException("parentId", "would make the tree deeper than " + MAX_DEPTH + " levels");
        }
    }

    /** Levels from this shelf down to its deepest shelf, itself included. */
    private static int height(Integer id, Map<Integer, List<Category>> byParent) {
        return 1 + byParent.getOrDefault(id, List.of()).stream().mapToInt(c -> height(c.getId(), byParent)).max().orElse(0);
    }

    private static Set<Integer> subtree(Integer id, Map<Integer, List<Category>> byParent) {
        Set<Integer> ids = new HashSet<>();
        List<Integer> todo = new ArrayList<>(List.of(id));
        while (!todo.isEmpty()) {
            Integer next = todo.removeLast();
            if (ids.add(next)) {
                byParent.getOrDefault(next, List.of()).forEach(c -> todo.add(c.getId()));
            }
        }
        return ids;
    }

    private static Map<Integer, List<Category>> byParent(List<Category> all) {
        Map<Integer, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        return byParent;
    }

    private String parentName(Integer parentId) {
        return parentId == null ? "Top level" : categoryRepository.findById(parentId).map(Category::getName).orElse("?");
    }

    private Category saveChecked(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            // a sibling already has this name (uq_categories_parent_name / uq_categories_root_name), or the slug race
            throw new CatalogConflictException("category_already_exists");
        }
    }

    private static CatalogNotFoundException notFound() {
        return new CatalogNotFoundException("category_not_found");
    }

    /** Root-first path to a category, for breadcrumbs on the product page. */
    @Transactional(readOnly = true)
    public List<CategoryRef> breadcrumb(Category category) {
        List<CategoryRef> path = new ArrayList<>();
        Category current = category;
        // Depth guard: update and move refuse a cycle and keep the tree within MAX_DEPTH, but a loop that
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
        category.setPosition(categoryRepository.nextPosition(request.parentId()));
        try {
            Category saved = categoryRepository.saveAndFlush(category);
            auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Added the category {target}")
                    .target(AuditTarget.CATEGORY, saved.getId(), saved.getName()));
            return toRef(saved);
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
