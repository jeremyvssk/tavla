// The staff's category tree: hidden shelves and counts, renaming, moving without cycles, ordering, and deleting only empty shelves.
package com.iloveshopping.catalog;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryAdminIT extends CatalogTestSupport {

    private String admin;
    private String tag;
    private int root;

    @BeforeEach
    void tree() throws Exception {
        admin = adminToken();
        tag = suffix();
        root = createCategory(admin, "Root " + tag, "root-" + tag, null);
    }

    @Test
    void theStaffTreeHasHiddenShelvesAndProductCounts_theShopsDoesNot() throws Exception {
        int hidden = createCategory(admin, "Hidden " + tag, "hidden-" + tag, root);
        createProduct(admin, "On hidden " + tag, "x", "10.00", hidden, null);
        update(hidden, "Hidden " + tag, "hidden-" + tag, root, false).andExpect(status().isOk());

        mockMvc.perform(auth(get("/staff/categories"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].children[0].active".formatted(root)).value(false))
                .andExpect(jsonPath("$[?(@.id == %d)].children[0].productCount".formatted(root)).value(1));
        String shop = mockMvc.perform(get("/categories")).andReturn().getResponse().getContentAsString();
        List<Integer> children = JsonPath.read(shop, "$[?(@.id == %d)].children[*].id".formatted(root));
        assertThat(children).doesNotContain(hidden);
    }

    @Test
    void aShelfCantGoUnderItselfOrUnderAShelfBelowIt() throws Exception {
        int child = createCategory(admin, "Child " + tag, "child-" + tag, root);
        int grandchild = createCategory(admin, "Grandchild " + tag, "grandchild-" + tag, child);

        update(root, "Root " + tag, "root-" + tag, root, true)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.parentId").exists());
        update(root, "Root " + tag, "root-" + tag, grandchild, true)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.parentId").exists());
        move(root, grandchild, null).andExpect(status().isBadRequest());
        update(root, "Root " + tag, "root-" + tag, 999_999, true)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.parentId").value("does not exist"));
        assertThat(jdbc.queryForObject("SELECT parent_id FROM categories WHERE id = ?", Integer.class, root)).isNull();
    }

    @Test
    void renamingChecksTheSlugAndSiblingNames() throws Exception {
        int a = createCategory(admin, "A " + tag, "a-" + tag, root);
        createCategory(admin, "B " + tag, "b-" + tag, root);

        update(a, "A renamed " + tag, "b-" + tag, root, true)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("slug_already_exists"));
        update(a, "B " + tag, "a-" + tag, root, true)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("category_already_exists"));
        update(a, "A renamed " + tag, "a-renamed-" + tag, root, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("a-renamed-" + tag));
    }

    @Test
    void movingPutsAShelfBeforeAnother_andTheShopFollowsThatOrder() throws Exception {
        int a = createCategory(admin, "A " + tag, "a-" + tag, root);
        int b = createCategory(admin, "B " + tag, "b-" + tag, root);
        int c = createCategory(admin, "C " + tag, "c-" + tag, root);

        move(c, root, a).andExpect(status().isOk());
        assertThat(childIds()).containsExactly(c, a, b);
        move(c, root, null).andExpect(status().isOk());
        assertThat(childIds()).containsExactly(a, b, c);
        move(a, root, 999_999).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.beforeId").exists());
    }

    @Test
    void onlyAnEmptyShelfCanBeDeleted() throws Exception {
        int withChild = createCategory(admin, "Parent " + tag, "parent-" + tag, root);
        createCategory(admin, "Kid " + tag, "kid-" + tag, withChild);
        int withProduct = createCategory(admin, "Stocked " + tag, "stocked-" + tag, root);
        createProduct(admin, "Thing " + tag, "x", "10.00", withProduct, null);
        int empty = createCategory(admin, "Empty " + tag, "empty-" + tag, root);

        mockMvc.perform(auth(delete("/categories/{id}", withChild), admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("category_not_empty"));
        mockMvc.perform(auth(delete("/categories/{id}", withProduct), admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("category_not_empty"));
        mockMvc.perform(auth(delete("/categories/{id}", empty), admin)).andExpect(status().isNoContent());
        mockMvc.perform(auth(delete("/categories/{id}", empty), admin)).andExpect(status().isNotFound());
    }

    @Test
    void categoryWritesAreForCatalogStaffOnly() throws Exception {
        mockMvc.perform(json(put("/categories/{id}", root), staff("SUPPORT").token(), "{}")).andExpect(status().isForbidden());
        mockMvc.perform(auth(delete("/categories/{id}", root), customerToken())).andExpect(status().isForbidden());
        mockMvc.perform(auth(get("/staff/categories"), staff("SUPPORT").token())).andExpect(status().isForbidden());
    }

    private List<Integer> childIds() throws Exception {
        String tree = mockMvc.perform(get("/categories")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(tree, "$[?(@.id == %d)].children[*].id".formatted(root));
    }

    private org.springframework.test.web.servlet.ResultActions update(int id, String name, String slug, Integer parentId,
                                                                       boolean active) throws Exception {
        return mockMvc.perform(json(put("/categories/{id}", id), admin, """
                {"name":"%s","slug":"%s","parentId":%s,"active":%s}""".formatted(name, slug, parentId, active)));
    }

    private org.springframework.test.web.servlet.ResultActions move(int id, Integer parentId, Integer beforeId) throws Exception {
        return mockMvc.perform(json(put("/categories/{id}/position", id), admin, """
                {"parentId":%s,"beforeId":%s}""".formatted(parentId, beforeId)));
    }
}
