// Security probes: injection into search parameters, oversized and malformed input, mass assignment, hostile uploads.
package com.iloveshopping.security;

import com.iloveshopping.support.CatalogTestSupport;
import com.iloveshopping.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every probe must end in a 4xx (or a harmless 200 for text that is only ever data), and no
 * response body may carry SQL, a Java class name, a stack frame or a filesystem path.
 */
class InputValidationIT extends CatalogTestSupport {

    private long productsBefore;

    @BeforeEach
    void countProducts() {
        productsBefore = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);
    }

    // ---- SQL injection -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "' OR '1'='1",
            "walnut'; DROP TABLE products; --",
            "\" UNION SELECT id, email, password_hash FROM users --",
            "') OR 1=1 --",
            "!@#$%^&*()<>|&:"})
    void searchText_isOnlyEverData(String probe) throws Exception {
        // websearch_to_tsquery receives the probe as a bound parameter: no rows leak, nothing breaks.
        MvcResult result = mockMvc.perform(get("/products").param("q", probe))
                .andExpect(status().isOk())
                .andReturn();
        assertNoLeak(result);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash", "@example.com");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isEqualTo(productsBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "price_asc; DROP TABLE products",
            "p.price",
            "(SELECT 1)",
            "price_asc,(case when (select 1)=1 then p.price else p.id end)",
            "PRICE_ASC"})
    void sort_outsideTheAllowlist_isRejected(String probe) throws Exception {
        assertNoLeak(mockMvc.perform(get("/products").param("sort", probe))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sort").exists())
                .andReturn());
    }

    @Test
    void filterParameters_rejectNonNumbersAndNonSlugs() throws Exception {
        expect400(get("/products").param("minPrice", "0 OR 1=1"));
        expect400(get("/products").param("minRating", "1; DELETE FROM product_reviews"));
        expect400(get("/products").param("category", "chess' OR '1'='1"));
        expect400(get("/products").param("brand", "rank-and-file", "x') OR ('1'='1"));
        expect400(get("/products").param("page", "-1"));
        expect400(get("/products").param("size", "100000"));
    }

    @Test
    void suggestionWildcards_areEscaped() throws Exception {
        mockMvc.perform(get("/search/suggestions").param("q", "__"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    // ---- Oversized and malformed input -------------------------------------------------------

    @Test
    void oversizedStrings_areRejected() throws Exception {
        expect400(get("/products").param("q", "a".repeat(201)));
        expect400(get("/search/suggestions").param("q", "a".repeat(101)));

        String longName = "x".repeat(256);
        mockMvc.perform(json(post("/products"), adminToken(), """
                        {"name":"%s","price":1,"stockQuantity":1,"categoryId":1}""".formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void deeplyNestedJson_isRejectedBeforeItReachesTheService() throws Exception {
        String nested = "{\"a\":".repeat(5_000) + "1" + "}".repeat(5_000);
        String body = """
                {"name":"Deep","price":1,"stockQuantity":1,"categoryId":1,"attributes":%s}""".formatted(nested);
        assertNoLeak(mockMvc.perform(json(post("/products"), adminToken(), body))
                .andExpect(status().isBadRequest())
                .andReturn());
    }

    @Test
    void malformedJson_isA400WithAGenericBody() throws Exception {
        assertNoLeak(mockMvc.perform(json(post("/products"), adminToken(), "{\"name\": \"unterminated"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed_request"))
                .andReturn());
    }

    // ---- Mass assignment ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"\"id\":\"00000000-0000-0000-0000-000000000001\"", "\"averageRating\":5",
            "\"reviewCount\":9999", "\"weightLbs\":1", "\"createdAt\":\"2000-01-01T00:00:00Z\""})
    void serverOwnedFields_cannotBeWritten(String extraField) throws Exception {
        int category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'go'", Integer.class);
        String body = """
                {"name":"Mass Assignment","price":1,"stockQuantity":1,"categoryId":%d,%s}""".formatted(category, extraField);
        mockMvc.perform(json(post("/products"), adminToken(), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed_request"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isEqualTo(productsBefore);
    }

    @Test
    void registration_cannotSelfAssignTheAdminRole() throws Exception {
        mockMvc.perform(json(post("/auth/register"), null, """
                        {"email":"sneaky-%s@example.com","password":"password123","fullName":"Sneaky","role":"ADMIN"}"""
                        .formatted(suffix())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void brandLogo_rejectsScriptUrls() throws Exception {
        mockMvc.perform(json(post("/brands"), adminToken(), """
                        {"name":"XSS","slug":"xss-%s","logoUrl":"javascript:alert(1)"}""".formatted(suffix())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.logoUrl").exists());
    }

    // ---- Hostile uploads ---------------------------------------------------------------------

    @Test
    void scriptRenamedToJpg_isRejectedByItsBytes() throws Exception {
        expectUploadRejected(new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                "<?php system($_GET['c']); ?>".getBytes()), "unsupported_image_type");
    }

    @Test
    void svg_isRejected_evenWithAnImageContentType() throws Exception {
        expectUploadRejected(new MockMultipartFile("file", "logo.png", "image/png",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes()), "unsupported_image_type");
    }

    @Test
    void truncatedImage_isRejected() throws Exception {
        byte[] png = TestImages.png(20, 20);
        byte[] truncated = java.util.Arrays.copyOf(png, 30);
        expectUploadRejected(new MockMultipartFile("file", "cut.png", "image/png", truncated), "invalid_image");
    }

    @Test
    void decompressionBomb_isRejectedFromItsHeader() throws Exception {
        expectUploadRejected(new MockMultipartFile("file", "bomb.png", "image/png",
                TestImages.pngClaimingSize(50_000, 50_000)), "image_dimensions_too_large");
    }

    @Test
    void pathTraversalFilename_isIgnored_andPolyglotPayloadIsStripped() throws Exception {
        String admin = adminToken();
        String productId = uploadTarget(admin);
        MockMultipartFile file = new MockMultipartFile("file", "../../../../etc/cron.d/evil.png", "image/png",
                TestImages.pngWithTrailingScript());

        String url = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(auth(multipart("/products/{id}/images", productId).file(file), admin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.url");

        assertThat(url).doesNotContain("..", "evil", "cron");
        byte[] stored = Files.readAllBytes(imagesDir.resolve(url.substring("/images/".length())));
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("<script>");
    }

    // ---- helpers -----------------------------------------------------------------------------

    private void expectUploadRejected(MockMultipartFile file, String code) throws Exception {
        String admin = adminToken();
        String productId = uploadTarget(admin);
        long filesBefore = countStoredFiles();
        assertNoLeak(mockMvc.perform(auth(multipart("/products/{id}/images", productId).file(file), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(code))
                .andReturn());
        assertThat(countStoredFiles()).isEqualTo(filesBefore);
    }

    private String uploadTarget(String admin) throws Exception {
        int category = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'go'", Integer.class);
        return createProduct(admin, "Upload Target " + suffix(), "", "10.00", category, null);
    }

    private long countStoredFiles() throws Exception {
        if (!Files.exists(imagesDir.resolve("products"))) {
            return 0;
        }
        try (Stream<?> files = Files.list(imagesDir.resolve("products"))) {
            return files.count();
        }
    }

    private void expect400(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        assertNoLeak(mockMvc.perform(request).andExpect(status().isBadRequest()).andReturn());
    }

    private static void assertNoLeak(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("select ")
                .doesNotContain("org.", "java.", "Exception", "at com.", "/var/app", "/tmp", "SQLState", "psql");
    }
}
