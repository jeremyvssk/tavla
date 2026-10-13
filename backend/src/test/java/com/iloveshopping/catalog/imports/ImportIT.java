// The bulk upload: a check that changes nothing, an import that is all or nothing, and every way a file or a row can be refused.
package com.iloveshopping.catalog.imports;

import com.iloveshopping.support.CatalogTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportIT extends CatalogTestSupport {

    private static final String HEADER = "sku,name,description,price,stock,category,brand,image_url,image_alt\n";

    private String admin;
    private String tag;
    private String shelf;
    private String brand;

    @BeforeEach
    void shop() throws Exception {
        admin = adminToken();
        tag = suffix();
        shelf = "import-" + tag;
        brand = "brand-" + tag;
        createCategory(admin, "Import " + tag, shelf, null);
        createBrand(admin, "Brand " + tag, brand);
    }

    @Test
    void theCheckChangesNothing_andImportWritesEveryRowWithItsPhotos() throws Exception {
        String csv = HEADER
                + "A-%s,Walnut board,\"Hand made, oiled\",65.00,12,%s,%s,https://cdn.example.com/a.jpg | https://cdn.example.com/a2.jpg,Walnut board\n"
                .formatted(tag, shelf, brand)
                + "B-%s,Clock,,30,0,%s,Brand %s,,\n".formatted(tag, shelf, tag);

        String job = id(upload(admin, "products.csv", csv)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CHECKED"))
                .andExpect(jsonPath("$.format").value("csv"))
                .andExpect(jsonPath("$.rows").value(2))
                .andExpect(jsonPath("$.counts.new").value(2))
                .andExpect(jsonPath("$.counts.error").value(0))
                .andExpect(jsonPath("$.counts.photos").value(2)));
        assertThat(productCount("A-" + tag)).isZero();

        confirm(admin, job, "update")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.counts.done").value(2));
        assertThat(productCount("a-" + tag)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT p.description FROM products p WHERE p.sku = ?""", String.class, "A-" + tag)).isEqualTo("Hand made, oiled");
        assertThat(jdbc.queryForList("""
                SELECT i.url FROM product_images i JOIN products p ON p.id = i.product_id WHERE p.sku = ? ORDER BY i.display_order""",
                String.class, "A-" + tag)).containsExactly("https://cdn.example.com/a.jpg", "https://cdn.example.com/a2.jpg");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id = ? AND action = 'Imported {target}'",
                Integer.class, job)).isEqualTo(1);
    }

    @Test
    void anUpdateChangesOnlyTheColumnsWithAValue_andSkipLeavesTheProductAlone() throws Exception {
        String existing = imported("sku,name,description,price,stock,category\nU-%s,Old name,Kept text,10.00,5,%s\n".formatted(tag, shelf));

        String job = id(upload(admin, "prices.csv", "sku,price,description\nu-%s,15,\n".formatted(tag))
                .andExpect(jsonPath("$.counts.update").value(1))
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].column").value("price"))
                .andExpect(jsonPath("$.changes[0].from").value("10.00"))
                .andExpect(jsonPath("$.changes[0].to").value("15.00")));
        confirm(admin, job, "skip").andExpect(jsonPath("$.counts.done").value(0));
        assertThat(field(existing, "price::text")).isEqualTo("10.00");

        String again = id(upload(admin, "prices.csv", "sku,price,description\nu-%s,15,\n".formatted(tag)));
        confirm(admin, again, "update").andExpect(jsonPath("$.counts.done").value(1));
        assertThat(field(existing, "price::text")).isEqualTo("15.00");
        assertThat(field(existing, "name")).isEqualTo("Old name");
        assertThat(field(existing, "description")).isEqualTo("Kept text");
    }

    @Test
    void everyBadRowIsListed_andTheFileCantBeImportedUntilItIsFixed() throws Exception {
        String csv = HEADER
                + "G-%s,Good,,10.00,1,%s,,,\n".formatted(tag, shelf)
                + "E1-%s,No such shelf,,10.00,1,no-such-shelf-%s,,,\n".formatted(tag, tag)
                + "E2-%s,Bad price,,12€,1,%s,,,\n".formatted(tag, shelf)
                + "G-%s,Same SKU again,,10.00,1,%s,,,\n".formatted(tag.toUpperCase(), shelf)
                + "E4-%s,=CMD(),,10.00,1,%s,,,\n".formatted(tag, shelf)
                + "E5-%s,,,10.00,1,%s,,,\n".formatted(tag, shelf)
                + "E6-%s,Plain http photo,,10.00,1,%s,,http://cdn.example.com/x.jpg,\n".formatted(tag, shelf)
                + "E7-%s,Unknown brand,,10.00,1,%s,nobody-%s,,\n".formatted(tag, shelf, tag);

        String body = upload(admin, "mixed.csv", csv)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.counts.new").value(1))
                .andExpect(jsonPath("$.counts.error").value(7))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> errors = JsonPath.read(body, "$.errors");
        assertThat(errors).extracting(e -> e.get("line") + ":" + e.get("column")).contains(
                "3:category", "4:price", "5:sku", "6:name", "7:name", "8:image_url", "9:brand");

        String job = JsonPath.read(body, "$.id");
        confirm(admin, job, "update").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("import_has_errors"));
        assertThat(productCount("g-" + tag)).isZero();

        String refused = mockMvc.perform(auth(get("/staff/imports/{id}/errors", job), admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(refused).startsWith("﻿" + HEADER.replace("\n", "\r\n"))
                .contains("E1-" + tag).contains("'=CMD()").doesNotContain("Good");
    }

    @Test
    void aFileThatCantBeReadAtAllIsRefusedWithTheReason() throws Exception {
        upload(admin, "products.csv", "sku,colour\nA,red\n").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_import_file"))
                .andExpect(jsonPath("$.fields.file").value(containsString("\"colour\"")));
        upload(admin, "products.csv", "name,price\nA,1\n").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.file").value(containsString("no sku column")));
        upload(admin, "products.txt", "sku\nA\n").andExpect(status().isBadRequest());
        upload(admin, "products.json", "{\"sku\":\"A\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.file").value(containsString("list of products")));
        upload(admin, "products.csv", "sku\n").andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/staff/imports").file(new MockMultipartFile("file", "latin1.csv", "text/csv",
                        new byte[] {'s', 'k', 'u', '\n', (byte) 0xE9, '\n'})).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").value(containsString("UTF-8")));
        upload(admin, "big.csv", "sku\n" + "A\n".repeat(5_001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.file").value(containsString("5000")));
    }

    @Test
    void aJsonFileTakesTheSameColumns_withAVariantObjectAndAPhotoList() throws Exception {
        String json = """
                [{"sku":"J-%s","name":"Travel set","price":19.9,"stock":3,"category":"%s",
                  "variant_group":"travel-%s","variant":{"Colour":"Red"},"image_url":["https://cdn.example.com/j.jpg"]},
                 {"sku":"K-%s","name":"Bad","price":1,"stock":1,"category":"%s","colour":"blue"}]"""
                .formatted(tag, shelf, tag, tag, shelf);

        String body = upload(admin, "products.json", json)
                .andExpect(jsonPath("$.format").value("json"))
                .andExpect(jsonPath("$.counts.new").value(1))
                .andExpect(jsonPath("$.counts.error").value(1))
                .andExpect(jsonPath("$.errors[0].line").value(2))
                .andExpect(jsonPath("$.errors[0].column").value("colour"))
                .andReturn().getResponse().getContentAsString();
        String refused = mockMvc.perform(auth(get("/staff/imports/{id}/errors", (String) JsonPath.read(body, "$.id")), admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("\"colour\" : \"blue\"").doesNotContain("Travel set");

        String clean = id(upload(admin, "products.json", json.replaceFirst(",\\s*\\{\"sku\":\"K-.*}]", "]")));
        confirm(admin, clean, "update").andExpect(jsonPath("$.status").value("DONE"));
        assertThat(jdbc.queryForObject("SELECT attributes->'variant'->>'Colour' FROM products WHERE sku = ?", String.class, "J-" + tag))
                .isEqualTo("Red");
        assertThat(jdbc.queryForObject("SELECT price::text FROM products WHERE sku = ?", String.class, "J-" + tag)).isEqualTo("19.90");
    }

    @Test
    void salesCantMoveAPriceByMoreThanHalfThroughAFileEither() throws Exception {
        imported("sku,name,price,stock,category\nS-%s,Board,10.00,5,%s\n".formatted(tag, shelf));
        String file = "sku,price\nS-%s,100\n".formatted(tag);

        upload(staff("SALES").token(), "prices.csv", file)
                .andExpect(jsonPath("$.counts.error").value(1))
                .andExpect(jsonPath("$.errors[0].column").value("price"))
                .andExpect(jsonPath("$.errors[0].problem").value(containsString("50%")));
        upload(admin, "prices.csv", file).andExpect(jsonPath("$.counts.error").value(0));
    }

    @Test
    void importRunsOnce_andAShopThatChangedSinceTheCheckStopsIt() throws Exception {
        String job = id(upload(admin, "a.csv", "sku,name,price,stock,category\nO-%s,Once,1,1,%s\n".formatted(tag, shelf)));
        confirm(admin, job, "update").andExpect(status().isOk());
        confirm(admin, job, "update").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("import_not_ready"));

        String gone = "gone-" + tag;
        int shelfId = createCategory(admin, "Gone " + tag, gone, null);
        String late = id(upload(admin, "b.csv", "sku,name,price,stock,category\nL-%s,Late,1,1,%s\n".formatted(tag, gone)));
        mockMvc.perform(auth(delete("/categories/{id}", shelfId), admin)).andExpect(status().isNoContent());

        confirm(admin, late, "update").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("import_outdated"));
        mockMvc.perform(auth(get("/staff/imports/{id}", late), admin))
                .andExpect(jsonPath("$.status").value("CHECKED"))
                .andExpect(jsonPath("$.counts.error").value(1))
                .andExpect(jsonPath("$.errors[0].column").value("category"));
        assertThat(productCount("l-" + tag)).isZero();
    }

    @Test
    void anExportedFileUploadsAgainWithNothingToChange() throws Exception {
        imported("sku,name,description,price,stock,category,brand,ean,weight_kg,variant,image_url\n"
                + "R-%s,=Round trip,\"Two\nlines\",12.50,4,%s,%s,5901234123457,0.45,Size: L,https://cdn.example.com/r.jpg\n"
                .formatted(tag, shelf, brand).replace("=Round", "'=Round"));

        byte[] export = mockMvc.perform(auth(get("/staff/products/export"), admin).param("category", shelf))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        mockMvc.perform(multipart("/staff/imports").file(new MockMultipartFile("file", "export.csv", "text/csv", export))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.counts.update").value(1))
                .andExpect(jsonPath("$.counts.error").value(0))
                .andExpect(jsonPath("$.counts.photos").value(0))
                .andExpect(jsonPath("$.changes.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT name FROM products WHERE sku = ?", String.class, "R-" + tag)).isEqualTo("=Round trip");
    }

    @Test
    void onlyCatalogStaffUpload() throws Exception {
        upload(staff("SUPPORT").token(), "a.csv", "sku\nA\n").andExpect(status().isForbidden());
        upload(customerToken(), "a.csv", "sku\nA\n").andExpect(status().isForbidden());
        mockMvc.perform(json(post("/staff/imports/{id}/confirm", java.util.UUID.randomUUID()), admin, "{\"onMatch\":\"all\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/staff/imports/{id}/confirm", java.util.UUID.randomUUID()), admin, "{\"onMatch\":\"update\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theExampleFilesInDocs_checkAsTheirNamesSay() throws Exception {
        // docs/examples is what the README hands a reviewer to try: the good files must stay good against the seed.
        Path examples = Path.of("..", "docs", "examples");
        upload(admin, "products-good.csv", Files.readString(examples.resolve("products-good.csv")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rows").value(5))
                .andExpect(jsonPath("$.counts.error").value(0));
        upload(admin, "products.json", Files.readString(examples.resolve("products.json")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.counts.error").value(0));

        String body = upload(admin, "products-bad.csv", Files.readString(examples.resolve("products-bad.csv")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> errors = JsonPath.read(body, "$.errors");
        // every line but the fine one (line 2) is refused, each for the reason its name gives
        assertThat(errors).extracting(e -> (Integer) e.get("line")).containsOnly(3, 4, 5, 6, 7, 8, 9, 10, 11);
        assertThat(errors).extracting(e -> e.get("line") + ":" + e.get("column")).contains(
                "3:category", "4:price", "5:price", "6:sku", "7:name", "8:name", "9:image_url", "10:brand", "11:stock");
    }

    private ResultActions upload(String token, String name, String content) throws Exception {
        return mockMvc.perform(multipart("/staff/imports")
                .file(new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions confirm(String token, String job, String onMatch) throws Exception {
        return mockMvc.perform(json(post("/staff/imports/{id}/confirm", job), token, "{\"onMatch\":\"%s\"}".formatted(onMatch)));
    }

    /** Uploads and imports a file that must check clean; returns the id of the product with the first row's SKU. */
    private String imported(String csv) throws Exception {
        String job = id(upload(admin, "seed.csv", csv).andExpect(jsonPath("$.counts.error").value(0)));
        confirm(admin, job, "update").andExpect(jsonPath("$.status").value("DONE"));
        String sku = csv.split("\n")[1].split(",")[0];
        return jdbc.queryForObject("SELECT id::text FROM products WHERE lower(sku) = lower(?)", String.class, sku);
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private int productCount(String sku) {
        return jdbc.queryForObject("SELECT count(*) FROM products WHERE lower(sku) = lower(?)", Integer.class, sku);
    }

    private String field(String productId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM products WHERE id = ?::uuid", String.class, productId);
    }
}
