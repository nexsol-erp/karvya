package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.application.settings.SettingsService;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.NotificationStatus;
import com.karvya.store.domain.model.Role;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.EmailNotificationRepository;
import com.karvya.store.domain.repository.RoleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Settings, categories, and customer administration. */
class AdminSettingsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SettingsService settings;
    @Autowired private EmailNotificationRepository notifications;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "an-admin-password-1";
    private String adminEmail;

    @BeforeEach
    void createAdmin() {
        adminEmail = uniqueEmail("setadmin");
        transactionTemplate.executeWithoutResult(status -> {
            AppUser admin = AppUser.create(adminEmail, "Settings Admin",
                    passwordEncoder.encode(PASSWORD), null);
            admin.addRole(roles.findByCode(Role.ADMIN).orElseThrow());
            users.save(admin);
        });
    }

    @AfterEach
    void restoreSettings() {
        settings.put(SettingsService.DELIVERY_CHARGE, "0.00", "test");
        settings.put("policy.shipping", "[PLACEHOLDER] Describe your delivery timelines.", "test");
        settings.put(SettingsService.STORE_NAME, "Karvya", "test");
    }

    private Cookie[] adminSession() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", adminEmail, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    private MvcResult putSettings(Cookie[] admin, Map<String, String> values) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/settings").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("values", values))))
                .andReturn();
    }

    // ---- settings ---------------------------------------------------------

    @Test
    @DisplayName("settings are listed with their type and placeholder state")
    void listsSettings() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings").cookie(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'delivery.charge')].valueType")
                        .value(org.hamcrest.Matchers.hasItem("DECIMAL")))
                // seeded copy is still flagged for review
                .andExpect(jsonPath("$[?(@.key == 'policy.shipping')].placeholder")
                        .value(org.hamcrest.Matchers.hasItem(true)));
    }

    @Test
    @DisplayName("a delivery charge is normalised to two decimals")
    void normalisesDecimalSettings() throws Exception {
        Cookie[] admin = adminSession();

        putSettings(admin, Map.of(SettingsService.DELIVERY_CHARGE, "80"))
                .getResponse();

        assertThat(settings.getMoney(SettingsService.DELIVERY_CHARGE, null).toPlainString())
                .isEqualTo("80.00");
    }

    /**
     * Every bad value, not just the first, and each keyed to its own setting -
     * that is what lets the form mark the offending inputs instead of showing
     * one banner and leaving the reader to find them.
     */
    @Test
    @DisplayName("every rejected value is reported against its own field")
    void reportsEveryBadValueByField() throws Exception {
        Cookie[] admin = adminSession();

        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(SettingsService.DELIVERY_CHARGE, "eighty rupees");
        values.put(SettingsService.COLOUR_PRIMARY, "reddish");
        values.put(SettingsService.FONT_HEADING, "Comic Sans MS");
        values.put(SettingsService.STORE_NAME, "Karvya");

        mockMvc.perform(put("/api/v1/admin/settings").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("values", values))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors['delivery.charge']").isNotEmpty())
                .andExpect(jsonPath("$.errors['theme.colour_primary']").isNotEmpty())
                .andExpect(jsonPath("$.errors['theme.font_heading']").isNotEmpty())
                // the one good value is not reported as a problem
                .andExpect(jsonPath("$.errors['store.name']").doesNotExist());

        // and nothing at all was written, including the value that was fine
        assertThat(settings.getMoney(SettingsService.DELIVERY_CHARGE, null).toPlainString())
                .isEqualTo("0.00");
    }

    @Test
    @DisplayName("the message under a field does not repeat the field's own name")
    void fieldMessageReadsWellUnderTheField() throws Exception {
        Cookie[] admin = adminSession();

        mockMvc.perform(put("/api/v1/admin/settings").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("values",
                                Map.of(SettingsService.DELIVERY_CHARGE, "eighty rupees")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['delivery.charge']")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("delivery.charge"))))
                // but a lone failure shown as a banner still says which setting
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("delivery.charge")));
    }

    @Test
    @DisplayName("a value that does not match its type is refused")
    void refusesWrongTypedValue() throws Exception {
        Cookie[] admin = adminSession();

        mockMvc.perform(put("/api/v1/admin/settings").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("values",
                                Map.of(SettingsService.DELIVERY_CHARGE, "eighty rupees")))))
                // 400, not 422: a malformed value is a validation failure, and it
                // is now rendered exactly as a bean-validation one so the form
                // has a single way to mark a bad input
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://karvya.example/problems/validation-failed"))
                .andExpect(jsonPath("$.errors['delivery.charge']").isNotEmpty());

        // and nothing was written
        assertThat(settings.getMoney(SettingsService.DELIVERY_CHARGE, null).toPlainString())
                .isEqualTo("0.00");
    }

    /**
     * Settings are rendered into pages customers see. An administrator account
     * - or anyone who takes one over - must not be able to turn a policy page
     * into a script delivery mechanism.
     */
    @Test
    @DisplayName("rich text keeps its formatting and loses anything that could run")
    void sanitisesRichText() throws Exception {
        Cookie[] admin = adminSession();

        String hostile = "<p>Delivery is <strong>free</strong> over 2000."
                + "<script>alert('xss')</script>"
                + "<img src=x onerror=alert(1)>"
                + "<a href=\"javascript:alert(1)\">tap</a>"
                + "<a href=\"https://example.com\">a real link</a></p>";

        putSettings(admin, Map.of("policy.shipping", hostile));

        String stored = settings.getString("policy.shipping", "");

        assertThat(stored)
                .as("formatting survives")
                .contains("<strong>free</strong>")
                .contains("https://example.com");
        assertThat(stored)
                .as("nothing executable survives")
                .doesNotContain("<script")
                .doesNotContain("onerror")
                .doesNotContain("javascript:");
    }

    @Test
    @DisplayName("a plain string setting is stripped of markup entirely")
    void stripsMarkupFromPlainSettings() throws Exception {
        Cookie[] admin = adminSession();

        putSettings(admin, Map.of(SettingsService.STORE_NAME, "Karvya<script>alert(1)</script>"));

        assertThat(settings.getString(SettingsService.STORE_NAME, ""))
                .isEqualTo("Karvya")
                .doesNotContain("<");
    }

    @Test
    @DisplayName("an unknown setting key is refused rather than created")
    void refusesUnknownSettingKey() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings").cookie(adminSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("values", Map.of("made.up.key", "value")))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("settings are not writable without an admin session")
    void settingsRequireAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("values", Map.of(SettingsService.STORE_NAME, "Hijacked")))))
                .andExpect(status().isUnauthorized());
    }

    // ---- categories -------------------------------------------------------

    @Test
    @DisplayName("a category can be created with a derived slug and counted")
    void createsCategory() throws Exception {
        Cookie[] admin = adminSession();

        mockMvc.perform(post("/api/v1/admin/categories").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Hanging Planters", "displayOrder", 2,
                                "active", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("hanging-planters"))
                .andExpect(jsonPath("$.productCount").value(0));

        mockMvc.perform(get("/api/v1/admin/categories").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'bird-houses-and-nests')].productCount")
                        .value(org.hamcrest.Matchers.hasItem(5)));
    }

    // ---- customers --------------------------------------------------------

    @Test
    @DisplayName("customers can be searched, and never expose a password hash")
    void searchesCustomers() throws Exception {
        String customerEmail = uniqueEmail("findme");
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Findable Person", "email", customerEmail,
                                "password", "a-long-enough-password"))))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/v1/admin/customers").cookie(adminSession())
                        .param("q", "Findable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(customerEmail))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$");
    }

    @Test
    @DisplayName("disabling an account stops sign-in but keeps the customer")
    void disablesACustomer() throws Exception {
        String customerEmail = uniqueEmail("disableme");
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Disable Me", "email", customerEmail,
                                "password", "a-long-enough-password"))))
                .andExpect(status().isCreated());

        Long customerId = users.findByEmailNormalized(customerEmail).orElseThrow().getId();
        Cookie[] admin = adminSession();

        mockMvc.perform(patch("/api/v1/admin/customers/" + customerId + "/enabled")
                        .cookie(admin).with(csrf()).param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // they can no longer sign in, but the record is intact
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", customerEmail,
                                "password", "a-long-enough-password"))))
                .andExpect(status().isUnauthorized());

        assertThat(users.findByEmailNormalized(customerEmail)).isPresent();
    }

    @Test
    @DisplayName("an administrator account cannot be disabled from the customer screen")
    void refusesToDisableAnAdministrator() throws Exception {
        Long adminId = users.findByEmailNormalized(adminEmail).orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/admin/customers/" + adminId + "/enabled")
                        .cookie(adminSession()).with(csrf()).param("enabled", "false"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://karvya.example/problems/cannot-disable-administrator"));
    }

    /**
     * An administrator helping a locked-out customer sends a link. They never
     * see the token and cannot choose the password themselves.
     */
    @Test
    @DisplayName("an administrator can send a reset link but never learns the token")
    void sendsPasswordResetWithoutSeeingIt() throws Exception {
        String customerEmail = uniqueEmail("resetme");
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Reset Me", "email", customerEmail,
                                "password", "a-long-enough-password"))))
                .andExpect(status().isCreated());

        Long customerId = users.findByEmailNormalized(customerEmail).orElseThrow().getId();
        long before = notifications.count();

        String response = mockMvc.perform(post("/api/v1/admin/customers/" + customerId + "/password-reset")
                        .cookie(adminSession()).with(csrf()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(notifications.count() - before).isEqualTo(1);
        assertThat(response)
                .as("the response must not carry the token")
                .doesNotContain("token")
                .doesNotContain("reset-password?");

        var queued = notifications.findAll().stream()
                .filter(n -> n.getRecipient().equalsIgnoreCase(customerEmail))
                .findFirst().orElseThrow();
        assertThat(queued.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }
}
