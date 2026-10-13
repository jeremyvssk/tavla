// The shopper's own account: profile, saved addresses, data export, deletion that keeps the orders, billing, and the contact form.
package com.iloveshopping.user;

import com.iloveshopping.support.OrderTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountIT extends OrderTestSupport {

    private static final String HOME = """
            {"label":"Home","firstName":"Mart","lastName":"Kask","street":"Pärnu mnt 22","flat":null,"postcode":"10141",
             "city":"Tallinn","country":"EE","phone":"+372 5876 2210"}""";

    @Test
    void theProfileShowsAndChangesNameAndPhone_andThePhoneIsStoredEncrypted() throws Exception {
        String email = registerUser();
        String token = tokenFor(email);
        mockMvc.perform(auth(get("/users/me"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.phone").isEmpty());
        mockMvc.perform(json(patch("/users/me"), token, "{\"fullName\":\"Mart Kask\",\"phone\":\"+372 5876 2210\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+372 5876 2210"));
        assertThat(jdbc.queryForObject("SELECT phone FROM users WHERE email_hash = ?", String.class, emailHash(email))).startsWith("v1:");
        mockMvc.perform(json(patch("/users/me"), token, "{\"fullName\":\"\",\"phone\":\"12\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.fullName").exists()).andExpect(jsonPath("$.fields.phone").exists());
        mockMvc.perform(json(patch("/users/me"), token, "{\"fullName\":\"X\",\"email\":\"new@example.com\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void theFirstAddressIsTheDefault_aDefaultCantBeRemoved_andAnotherAccountsIdIsNotFound() throws Exception {
        String token = customerToken();
        String first = JsonPath.read(addAddress(token, HOME).andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].defaultDelivery").value(true))
                .andExpect(jsonPath("$[0].defaultBilling").value(true)).andReturn().getResponse().getContentAsString(), "$[0].id");
        String work = JsonPath.read(addAddress(token, HOME.replace("Home", "Work").replace("Pärnu mnt 22", "Narva mnt 7"))
                .andExpect(jsonPath("$.length()").value(2)).andReturn().getResponse().getContentAsString(), "$[1].id");

        mockMvc.perform(auth(delete("/users/me/addresses/{id}", first), token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("address_is_default"));
        mockMvc.perform(json(put("/users/me/addresses/{id}/default", work), token, "{\"kind\":\"delivery\"}"))
                .andExpect(jsonPath("$[?(@.id == '%s')].defaultDelivery".formatted(work)).value(true))
                .andExpect(jsonPath("$[?(@.id == '%s')].defaultDelivery".formatted(first)).value(false));
        // the billing default moves to the delivery default when its address goes
        mockMvc.perform(auth(delete("/users/me/addresses/{id}", first), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].defaultBilling").value(true));

        String stranger = customerToken();
        mockMvc.perform(auth(delete("/users/me/addresses/{id}", work), stranger)).andExpect(status().isNotFound());
        mockMvc.perform(json(put("/users/me/addresses/{id}", work), stranger, HOME)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT street FROM user_addresses WHERE id = ?::uuid", String.class, work)).startsWith("v1:");
    }

    @Test
    void anAddressGetsTheCheckoutsChecks_andTenIsTheMost() throws Exception {
        String token = customerToken();
        addAddress(token, HOME.replace("10141", "50090")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.postcode").value(org.hamcrest.Matchers.containsString("Tallinn")));
        addAddress(token, HOME.replace("\"EE\"", "\"SE\"")).andExpect(status().isBadRequest());
        for (int i = 0; i < 10; i++) {
            addAddress(token, HOME.replace("Home", "Home " + i)).andExpect(status().isCreated());
        }
        addAddress(token, HOME).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("address_limit"));
    }

    @Test
    void theExportHasTheOrdersAndReviews_butNoSecrets() throws Exception {
        String email = registerUser();
        String token = tokenFor(email);
        userAdd(token, product("30.00", 5), 1);
        placeOk(null, token);
        addAddress(token, HOME);

        String body = mockMvc.perform(auth(get("/users/me/export"), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"tavla-my-data.json\""))
                .andExpect(jsonPath("$.profile.email").value(email))
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.addresses[0].street").value("Pärnu mnt 22"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("passwordHash").doesNotContainIgnoringCase("secret").doesNotContain("$2a$");
    }

    @Test
    void deletingTheAccountNeedsThePassword_endsEverySession_andKeepsTheOrdersUnlinked() throws Exception {
        String email = registerUser();
        String token = tokenFor(email);
        userAdd(token, product("30.00", 5), 1);
        String order = placeOk(null, token);
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email_hash = ?", UUID.class, emailHash(email));

        mockMvc.perform(json(delete("/users/me"), token, "{\"password\":\"wrong-one\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(json(delete("/users/me"), token, "{\"password\":\"%s\"}".formatted(PASSWORD))).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT user_id FROM orders WHERE id = ?::uuid", UUID.class, order)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM carts WHERE user_id = ?", Integer.class, userId)).isZero();
        mockMvc.perform(auth(get("/users/me"), token)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))).andExpect(status().isUnauthorized());
    }

    @Test
    void aStaffAccountCantDeleteItself() throws Exception {
        mockMvc.perform(json(delete("/users/me"), staff("SUPPORT").token(), "{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("staff_account"));
    }

    @Test
    void aSeparateBillingAddressIsKept_encrypted_andGetsThePostcodeCheck() throws Exception {
        String cookie = guestAdd(null, product("30.00", 5), 1);
        String bad = checkout("standard").replace("\"shippingMethod\"",
                "\"billingAddress\":{\"name\":\"Kask OÜ\",\"street\":\"Tartu mnt 1\",\"postcode\":\"9\",\"city\":\"Tallinn\",\"country\":\"EE\"},\"shippingMethod\"");
        MvcResult refused = place(cookie, null, bad);
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(refused.getResponse().getContentAsString()).contains("billingAddress.postcode");

        MvcResult placed = place(cookie, null, bad.replace("\"postcode\":\"9\"", "\"postcode\":\"10111\""));
        assertThat(placed.getResponse().getStatus()).isEqualTo(201);
        String order = JsonPath.read(placed.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(as(get("/orders/{id}", order), cookie)).andExpect(jsonPath("$.billTo.name").value("Kask OÜ"));
        assertThat(jdbc.queryForObject("SELECT billing_name FROM orders WHERE id = ?::uuid", String.class, order)).startsWith("v1:");
    }

    @Test
    void theContactFormEmailsTheShopsInbox_withTheSenderToReplyTo() throws Exception {
        String marker = "Marker " + UUID.randomUUID();
        mockMvc.perform(json(post("/support/messages"), null, """
                        {"name":"Mart Kask","email":"mart@example.com","topic":"order","orderNumber":"TV-10001",
                         "message":"%s: where is my order? It has been a week."}""".formatted(marker)))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        await().atMost(Duration.ofSeconds(15)).until(() -> contactMail(marker) != null);
        SimpleMailMessage mail = contactMail(marker);
        assertThat(mail.getTo()).containsExactly("support@iloveshopping.local");
        assertThat(mail.getReplyTo()).isEqualTo("mart@example.com");
        assertThat(mail.getSubject()).isEqualTo("Contact form: An order, TV-10001, from Mart Kask");
        verify(mailSender, atLeastOnce()).send(any(SimpleMailMessage.class));
    }

    @Test
    void theContactFormRefusesHeaderTricksAndShortOrLongMessages() throws Exception {
        String ok = """
                {"name":"Mart","email":"mart@example.com","topic":"other","orderNumber":"","message":"%s"}""";
        mockMvc.perform(json(post("/support/messages"), null, ok.formatted("Too short"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.message").exists());
        mockMvc.perform(json(post("/support/messages"), null, ok.formatted("x".repeat(2001)))).andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/support/messages"), null, ok.formatted("A long enough message for the shop.")
                        .replace("\"Mart\"", "\"Mart\\r\\nBcc: everyone@example.com\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists());
        mockMvc.perform(json(post("/support/messages"), null, ok.formatted("A long enough message for the shop.")
                        .replace("\"other\"", "\"spam\""))).andExpect(status().isBadRequest());
    }

    private ResultActions addAddress(String token, String body) throws Exception {
        return mockMvc.perform(json(post("/users/me/addresses"), token, body));
    }

    private SimpleMailMessage contactMail(String marker) {
        List<SimpleMailMessage> sent = org.mockito.Mockito.mockingDetails(mailSender).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("send") && i.getArguments().length == 1
                        && i.getArguments()[0] instanceof SimpleMailMessage)
                .map(i -> (SimpleMailMessage) i.getArguments()[0])
                .filter(m -> m.getText() != null && m.getText().contains(marker))
                .toList();
        return sent.isEmpty() ? null : sent.getFirst();
    }
}
