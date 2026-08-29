package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.application.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The endpoint the storefront reads its administrator-editable values from.
 *
 * <p>These values used to be compiled into the JavaScript bundle by Vite, so a
 * number saved in the admin was silently ignored in favour of whatever had been
 * built weeks earlier. That is what this endpoint exists to prevent, and the
 * first test here is the one that would have caught it.
 */
class PublicSettingsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SettingsService settings;

    @AfterEach
    void restoreSettings() {
        settings.put(SettingsService.WHATSAPP_NUMBER, "", "test");
        settings.put(SettingsService.ADMIN_EMAIL, "", "test");
        settings.put(SettingsService.STORE_NAME, "Karvya", "test");
    }

    @Test
    @DisplayName("serves the number saved in settings, not a build-time value")
    void servesTheSavedNumber() throws Exception {
        settings.put(SettingsService.WHATSAPP_NUMBER, "919746800113", "test");

        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsAppNumber").value("919746800113"));
    }

    @Test
    @DisplayName("normalises however the number was typed into a wa.me-ready form")
    void normalisesTheNumber() throws Exception {
        // an administrator may reasonably type any of these
        settings.put(SettingsService.WHATSAPP_NUMBER, "00 91 97468 00113", "test");
        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(jsonPath("$.whatsAppNumber").value("919746800113"));

        settings.put(SettingsService.WHATSAPP_NUMBER, "+91 97468-00113", "test");
        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(jsonPath("$.whatsAppNumber").value("919746800113"));
    }

    @Test
    @DisplayName("reports no number rather than an empty one when none is set")
    void blankBecomesNull() throws Exception {
        settings.put(SettingsService.WHATSAPP_NUMBER, "   ", "test");

        // the storefront hides every WhatsApp link on null; "" would render a
        // button linking to https://wa.me/
        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(jsonPath("$.whatsAppNumber").doesNotExist());
    }

    @Test
    @DisplayName("is readable without signing in")
    void isAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").exists());
    }

    @Test
    @DisplayName("never exposes the internal admin address, only the public one")
    void doesNotLeakTheAdminAddress() throws Exception {
        settings.put(SettingsService.ADMIN_EMAIL, "internal-alerts@karvya.example", "test");
        settings.put(SettingsService.PUBLIC_EMAIL, "hello@karvya.example", "test");

        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(jsonPath("$.contactEmail").value("hello@karvya.example"))
                // the payload is unauthenticated: nothing operational belongs in it
                .andExpect(jsonPath("$.adminEmail").doesNotExist())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("internal-alerts"))));
    }
}
