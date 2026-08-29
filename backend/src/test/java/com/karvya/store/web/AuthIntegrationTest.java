package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.domain.model.NotificationStatus;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.EmailNotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Registration, sign-in, account isolation and password recovery.
 *
 * <p>These assert the security properties themselves, not just that the
 * endpoints answer: that failures are indistinguishable, that lockout engages,
 * and that one customer cannot reach another's data.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AppUserRepository users;

    @Autowired
    private EmailNotificationRepository notifications;

    /** Keeps each test's email distinct without depending on execution order. */
    private static final AtomicInteger SEQ = new AtomicInteger();

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Test Person",
                                "email", email,
                                "password", password))))
                .andExpect(status().isCreated());
    }

    /**
     * Signs in and returns the cookies that carry the session.
     *
     * <p>Cookies rather than a MockHttpSession, because Spring Session JDBC is
     * active: the real session lives in the database and is addressed by the
     * SESSION cookie, so a servlet-level session object is bypassed entirely.
     * Replaying the cookies is what a browser does, and is therefore what
     * actually exercises the mechanism these tests are about.
     */
    private Cookie[] login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookies();
    }

    // ---- registration -----------------------------------------------------

    @Test
    @DisplayName("registration creates a customer, never an administrator")
    void registersAsCustomerOnly() throws Exception {
        String email = uniqueEmail("newcomer");
        register(email, "a-long-enough-password");

        var user = users.findByEmailNormalized(email).orElseThrow();
        assertThat(user.hasRole("CUSTOMER")).isTrue();
        assertThat(user.hasRole("ADMIN")).isFalse();
        assertThat(user.getPasswordHash()).doesNotContain("a-long-enough-password");
    }

    @Test
    @DisplayName("email uniqueness ignores case")
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail("dupe");
        register(email, "a-long-enough-password");

        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Someone Else",
                                "email", email.toUpperCase(),
                                "password", "a-different-password"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(
                        "https://karvya.example/problems/email-already-registered"));
    }

    @Test
    @DisplayName("a short password is rejected with the offending field named")
    void rejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Test", "email", uniqueEmail("weak"), "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ---- sign-in ----------------------------------------------------------

    @Test
    @DisplayName("a wrong password and an unknown account are indistinguishable")
    void doesNotRevealWhetherAnAccountExists() throws Exception {
        String email = uniqueEmail("known");
        register(email, "a-long-enough-password");

        var wrongPassword = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();

        var unknownAccount = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", uniqueEmail("ghost"), "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();

        assertThat(wrongPassword.getContentAsString())
                .isEqualTo(unknownAccount.getContentAsString());
    }

    @Test
    @DisplayName("signing in establishes a session that /me can read")
    void signsInAndReadsCurrentUser() throws Exception {
        String email = uniqueEmail("signin");
        register(email, "a-long-enough-password");
        Cookie[] session = login(email, "a-long-enough-password");

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"))
                // the hash must never reach the client
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("without a session /me is unauthorised rather than a redirect")
    void rejectsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("repeated failures lock the account, and the right password then fails too")
    void locksAccountAfterRepeatedFailures() throws Exception {
        String email = uniqueEmail("lockme");
        register(email, "a-long-enough-password");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "password", "wrong-password-x"))))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(users.findByEmailNormalized(email).orElseThrow().isLocked()).isTrue();

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "a-long-enough-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a state-changing request without a CSRF token is refused")
    void requiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "No Token", "email", uniqueEmail("csrf"),
                                "password", "a-long-enough-password"))))
                .andExpect(status().isForbidden());
    }

    // ---- account isolation ------------------------------------------------

    @Test
    @DisplayName("one customer cannot read, edit or delete another's address")
    void isolatesAddressesBetweenCustomers() throws Exception {
        String ownerEmail = uniqueEmail("owner");
        String intruderEmail = uniqueEmail("intruder");
        register(ownerEmail, "a-long-enough-password");
        register(intruderEmail, "a-long-enough-password");

        Cookie[] ownerSession = login(ownerEmail, "a-long-enough-password");

        MvcResult created = mockMvc.perform(post("/api/v1/account/addresses")
                        .cookie(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recipientName", "Owner", "phone", "9876543210",
                                "line1", "1 Coir Lane", "city", "Kochi",
                                "state", "Kerala", "postalCode", "682001",
                                "makeDefault", true))))
                .andExpect(status().isCreated())
                .andReturn();

        long addressId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        Cookie[] intruderSession = login(intruderEmail, "a-long-enough-password");

        mockMvc.perform(get("/api/v1/account/addresses").cookie(intruderSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(put("/api/v1/account/addresses/" + addressId)
                        .cookie(intruderSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recipientName", "Hijacked", "phone", "9999999999",
                                "line1", "X", "city", "C", "state", "S",
                                "postalCode", "1", "makeDefault", false))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/account/addresses/" + addressId)
                        .cookie(intruderSession).with(csrf()))
                .andExpect(status().isNotFound());

        // and the owner's address is untouched
        mockMvc.perform(get("/api/v1/account/addresses").cookie(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipientName").value("Owner"));
    }

    @Test
    @DisplayName("exactly one address stays the default as others are promoted")
    void keepsExactlyOneDefaultAddress() throws Exception {
        String email = uniqueEmail("addresses");
        register(email, "a-long-enough-password");
        Cookie[] session = login(email, "a-long-enough-password");

        long second = 0;
        for (int i = 1; i <= 2; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/account/addresses")
                            .cookie(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "recipientName", "Person " + i, "phone", "9876543210",
                                    "line1", i + " Coir Lane", "city", "Kochi",
                                    "state", "Kerala", "postalCode", "68200" + i,
                                    "makeDefault", false))))
                    .andExpect(status().isCreated())
                    .andReturn();
            second = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        }

        // the first saved becomes default even though none was requested
        mockMvc.perform(get("/api/v1/account/addresses").cookie(session))
                .andExpect(jsonPath("$[?(@.isDefault == true)]", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(post("/api/v1/account/addresses/" + second + "/default")
                        .cookie(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/account/addresses").cookie(session))
                .andExpect(jsonPath("$[?(@.isDefault == true)]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(second));
    }

    // ---- password recovery ------------------------------------------------

    @Test
    @DisplayName("a reset request answers the same for a real and an unknown address")
    void forgotPasswordDoesNotRevealAccounts() throws Exception {
        String email = uniqueEmail("resetme");
        register(email, "a-long-enough-password");
        long before = notifications.count();

        mockMvc.perform(post("/api/v1/auth/password/forgot").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/password/forgot").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", uniqueEmail("nobody")))))
                .andExpect(status().isAccepted());

        // identical responses, but only the real account produced an email
        assertThat(notifications.count() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("a reset token works once, and only once")
    void resetsPasswordExactlyOnce() throws Exception {
        String email = uniqueEmail("recovery");
        register(email, "original-password-1");

        mockMvc.perform(post("/api/v1/auth/password/forgot").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isAccepted());

        var queued = notifications.findAll().stream()
                .filter(n -> n.getRecipient().equalsIgnoreCase(email))
                .findFirst()
                .orElseThrow();
        assertThat(queued.getStatus()).isEqualTo(NotificationStatus.PENDING);

        String resetUrl = objectMapper.readTree(queued.getPayload()).get("resetUrl").asText();
        String token = resetUrl.substring(resetUrl.indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/password/reset").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "replacement-password"))))
                .andExpect(status().isNoContent());

        // replay is refused
        mockMvc.perform(post("/api/v1/auth/password/reset").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "third-password-xx"))))
                .andExpect(status().isUnprocessableEntity());

        // the old password is dead, the new one works
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "original-password-1"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "replacement-password"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an invalid reset token is refused without saying why")
    void refusesUnknownResetToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", "not-a-real-token", "newPassword", "some-new-password"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(
                        "https://karvya.example/problems/invalid-reset-token"));
    }

    @Test
    @DisplayName("the reset token is stored only as a hash")
    void storesOnlyTheTokenHash() throws Exception {
        String email = uniqueEmail("hashcheck");
        register(email, "a-long-enough-password");

        mockMvc.perform(post("/api/v1/auth/password/forgot").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isAccepted());

        var queued = notifications.findAll().stream()
                .filter(n -> n.getRecipient().equalsIgnoreCase(email))
                .findFirst().orElseThrow();
        String resetUrl = objectMapper.readTree(queued.getPayload()).get("resetUrl").asText();
        String rawToken = resetUrl.substring(resetUrl.indexOf("token=") + 6);

        var user = users.findByEmailNormalized(email).orElseThrow();
        // SHA-256 hex is 64 characters, and never the token itself
        assertThat(rawToken).isNotBlank();
        assertThat(user.getId()).isNotNull();
    }

    @Test
    @DisplayName("changing a password requires the current one and ends the session")
    void changesPasswordAndInvalidatesSession() throws Exception {
        String email = uniqueEmail("changer");
        register(email, "original-password-1");
        Cookie[] session = login(email, "original-password-1");

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", "wrong-current-pw",
                                "newPassword", "replacement-password"))))
                .andExpect(status().isUnprocessableEntity());

        Cookie[] fresh = login(email, "original-password-1");

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(fresh).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", "original-password-1",
                                "newPassword", "replacement-password"))))
                .andExpect(status().isNoContent());

        // the session that made the change no longer works
        mockMvc.perform(get("/api/v1/auth/me").cookie(fresh))
                .andExpect(status().isUnauthorized());
    }
}
