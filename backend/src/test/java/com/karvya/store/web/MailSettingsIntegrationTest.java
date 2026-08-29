package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.application.settings.SettingsService;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.Role;
import com.karvya.store.domain.repository.AppUserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SMTP configured from the admin rather than from the environment.
 *
 * <p>The password is the only secret this table has ever held, so the two
 * things worth pinning are that it never comes back out, and that saving the
 * form around it does not wipe it.
 */
class MailSettingsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SettingsService settings;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "an-admin-password-1";
    private String adminEmail;

    @BeforeEach
    void createAdmin() {
        adminEmail = uniqueEmail("mailadmin");
        transactionTemplate.executeWithoutResult(status -> {
            AppUser admin = AppUser.create(adminEmail, "Mail Admin",
                    passwordEncoder.encode(PASSWORD), null);
            admin.addRole(roles.findByCode(Role.ADMIN).orElseThrow());
            users.save(admin);
        });
    }

    @AfterEach
    void clearMailSettings() {
        settings.put(SettingsService.MAIL_HOST, "", "test");
        settings.put(SettingsService.MAIL_PASSWORD, "", "test");
        settings.put(SettingsService.MAIL_FROM, "", "test");
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

    @Test
    @DisplayName("the SMTP password is never returned, only reported as stored")
    void neverReturnsTheSecret() throws Exception {
        Cookie[] admin = adminSession();

        putSettings(admin, Map.of(SettingsService.MAIL_PASSWORD, "a-real-smtp-key"));

        MvcResult listed = mockMvc.perform(get("/api/v1/admin/settings").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='mail.password')].value").value((Object) null))
                // reported as present, so the screen can say so without echoing it
                .andExpect(jsonPath("$[?(@.key=='mail.password')].unset").value(false))
                .andReturn();

        // and not anywhere else in the payload either
        assertThat(listed.getResponse().getContentAsString()).doesNotContain("a-real-smtp-key");
    }

    /**
     * The form cannot show a secret, so it submits an empty field for one it is
     * not changing. Treating that as a clear would wipe the SMTP password every
     * time an unrelated setting was saved.
     */
    @Test
    @DisplayName("an empty submission leaves a stored secret alone")
    void blankLeavesTheSecretAlone() throws Exception {
        Cookie[] admin = adminSession();

        putSettings(admin, Map.of(SettingsService.MAIL_PASSWORD, "a-real-smtp-key"));
        putSettings(admin, Map.of(
                SettingsService.MAIL_PASSWORD, "",
                SettingsService.MAIL_FROM, "orders@karvya.in"));

        assertThat(settings.find(SettingsService.MAIL_PASSWORD)).contains("a-real-smtp-key");
        assertThat(settings.find(SettingsService.MAIL_FROM)).contains("orders@karvya.in");
    }

    @Test
    @DisplayName("a new value does replace it")
    void aRealValueReplacesIt() throws Exception {
        Cookie[] admin = adminSession();

        putSettings(admin, Map.of(SettingsService.MAIL_PASSWORD, "first-key"));
        putSettings(admin, Map.of(SettingsService.MAIL_PASSWORD, "second-key"));

        assertThat(settings.find(SettingsService.MAIL_PASSWORD)).contains("second-key");
    }

    @Test
    @DisplayName("mail settings are not exposed to the storefront")
    void notPublic() throws Exception {
        adminSession();
        putSettings(adminSession(), Map.of(SettingsService.MAIL_PASSWORD, "a-real-smtp-key"));

        MvcResult publicSettings = mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(status().isOk())
                .andReturn();

        String body = publicSettings.getResponse().getContentAsString();
        assertThat(body).doesNotContain("a-real-smtp-key");
        assertThat(body).doesNotContain("mailPassword");
        assertThat(body).doesNotContain("mailHost");
    }

    @Test
    @DisplayName("the test message reports the failure rather than throwing it away")
    void testEmailReportsWhatHappened() throws Exception {
        Cookie[] admin = adminSession();

        // a host that cannot be reached, so the send is guaranteed to fail
        putSettings(admin, Map.of(
                SettingsService.MAIL_HOST, "smtp.invalid.example",
                SettingsService.MAIL_PORT, "587"));

        mockMvc.perform(post("/api/v1/admin/settings/mail/test").cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(false))
                .andExpect(jsonPath("$.source").value("site settings"))
                // the provider's own words are the point of the button
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("only an administrator can send one")
    void testEmailRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/settings/mail/test").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
