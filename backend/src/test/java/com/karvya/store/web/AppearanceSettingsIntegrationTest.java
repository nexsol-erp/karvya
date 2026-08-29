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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Colours and typefaces, which an administrator sets and the storefront reads
 * without signing in.
 */
class AppearanceSettingsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SettingsService settings;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "an-admin-password-1";
    private String adminEmail;

    @BeforeEach
    void createAdmin() {
        adminEmail = uniqueEmail("themeadmin");
        transactionTemplate.executeWithoutResult(status -> {
            AppUser admin = AppUser.create(adminEmail, "Theme Admin",
                    passwordEncoder.encode(PASSWORD), null);
            admin.addRole(roles.findByCode(Role.ADMIN).orElseThrow());
            users.save(admin);
        });
    }

    @AfterEach
    void restoreTheme() {
        settings.put(SettingsService.COLOUR_PRIMARY, "#A33B2E", "test");
        settings.put(SettingsService.FONT_HEADING, "Fraunces", "test");
    }

    private Cookie[] adminSession() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", adminEmail, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    private org.springframework.test.web.servlet.ResultActions putSettings(
            Cookie[] admin, Map<String, String> values) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/settings").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("values", values))));
    }

    @Test
    @DisplayName("a colour is saved and served to the storefront")
    void savesAndServesAColour() throws Exception {
        putSettings(adminSession(), Map.of(SettingsService.COLOUR_PRIMARY, "#2E5AA3"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colourPrimary").value("#2E5AA3"));
    }

    @Test
    @DisplayName("a colour is stored in one case, so two spellings are not two values")
    void normalisesCase() throws Exception {
        putSettings(adminSession(), Map.of(SettingsService.COLOUR_PRIMARY, "#2e5aa3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='theme.colour_primary')].value").value("#2E5AA3"));
    }

    @Test
    @DisplayName("a missing # is added rather than refused")
    void acceptsAColourWithoutTheHash() throws Exception {
        putSettings(adminSession(), Map.of(SettingsService.COLOUR_PRIMARY, "2E5AA3"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(jsonPath("$.colourPrimary").value("#2E5AA3"));
    }

    @Test
    @DisplayName("something that is not a colour is refused, and nothing is written")
    void refusesRubbishColour() throws Exception {
        putSettings(adminSession(), Map.of(SettingsService.COLOUR_PRIMARY, "reddish"))
                // a malformed value is a validation failure, reported against the
                // field so the Appearance screen can mark it
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['theme.colour_primary']").isNotEmpty());

        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(jsonPath("$.colourPrimary").value("#A33B2E"));
    }

    /**
     * The typeface list is not decoration. The Content-Security-Policy only
     * permits Google's font host, and each permitted name carries a fallback
     * stack - a name outside the list would load nothing and leave the shop in
     * a default sans-serif with no indication why.
     */
    @Test
    @DisplayName("a typeface outside the permitted list is refused")
    void refusesUnknownFont() throws Exception {
        putSettings(adminSession(), Map.of(SettingsService.FONT_HEADING, "Comic Sans MS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['theme.font_heading']").isNotEmpty());

        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(jsonPath("$.fontHeading").value("Fraunces"));
    }

    @Test
    @DisplayName("a permitted typeface is accepted")
    void acceptsAPermittedFont() throws Exception {
        putSettings(adminSession(), Map.of(SettingsService.FONT_HEADING, "Lora"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(jsonPath("$.fontHeading").value("Lora"));
    }

    @Test
    @DisplayName("appearance is readable without signing in, since the browser needs it")
    void isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colourBackground").exists())
                .andExpect(jsonPath("$.fontBody").exists())
                .andExpect(jsonPath("$.cornerRadius").exists());
    }

    @Test
    @DisplayName("only an administrator can change it")
    void requiresAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("values",
                                Map.of(SettingsService.COLOUR_PRIMARY, "#000000")))))
                .andExpect(status().isUnauthorized());
    }
}
