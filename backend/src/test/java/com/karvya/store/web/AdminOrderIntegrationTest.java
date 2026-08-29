package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.OrderStatus;
import com.karvya.store.domain.model.Role;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.CustomerOrderRepository;
import com.karvya.store.domain.repository.ProductRepository;
import com.karvya.store.domain.repository.RoleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The back office: authorisation, order transitions, and returning stock.
 *
 * <p>The seeded bootstrap administrator is not used here - the test profile
 * leaves its password empty on purpose - so each test makes its own.
 */
class AdminOrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProductRepository products;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String PASSWORD = "an-admin-password-1";

    private String adminEmail;

    @BeforeEach
    void createAdmin() {
        adminEmail = uniqueEmail("admin");
        transactionTemplate.executeWithoutResult(status -> {
            AppUser admin = AppUser.create(adminEmail, "Test Admin",
                    passwordEncoder.encode(PASSWORD), null);
            admin.addRole(roles.findByCode(Role.ADMIN).orElseThrow());
            users.save(admin);
        });
    }

    private Cookie[] signIn(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    private Cookie[] adminSession() throws Exception {
        return signIn(adminEmail, PASSWORD);
    }

    private Cookie[] customerSession() throws Exception {
        String email = uniqueEmail("shopper");
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Shopper", "email", email,
                                "password", "a-long-enough-password"))))
                .andExpect(status().isCreated());
        return signIn(email, "a-long-enough-password");
    }

    private String placeOrder(int quantity, String sku) throws Exception {
        Long productId = products.findBySku(sku).orElseThrow().getId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("productId", productId, "quantity", quantity)));
        body.put("deliveryName", "Asha Menon");
        body.put("deliveryPhone", "9876543210");
        body.put("addressLine1", "1 Coir Lane");
        body.put("city", "Kochi");
        body.put("state", "Kerala");
        body.put("postalCode", "682001");
        body.put("paymentMethodCode", "CASH_ON_DELIVERY");

        MvcResult result = mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("orderNumber").asText();
    }

    private int stockOf(String sku) {
        return products.findBySku(sku).orElseThrow().getStockQuantity();
    }

    // ---- authorisation ----------------------------------------------------

    @Test
    @DisplayName("a customer cannot reach the back office")
    void customersAreShutOutOfAdmin() throws Exception {
        Cookie[] shopper = customerSession();

        mockMvc.perform(get("/api/v1/admin/orders").cookie(shopper))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/dashboard").cookie(shopper))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous visitor cannot reach the back office")
    void anonymousIsShutOutOfAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The bootstrap credential is known to whoever deployed the application, so
     * an account still carrying it must be able to do nothing but replace it.
     */
    @Test
    @DisplayName("an admin owing a password change can do nothing else first")
    void forcedPasswordChangeBlocksEverythingElse() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            AppUser admin = users.findByEmailNormalized(adminEmail).orElseThrow();
            admin.setMustChangePassword(true);
            users.save(admin);
        });

        Cookie[] session = adminSession();

        mockMvc.perform(get("/api/v1/admin/orders").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://karvya.example/problems/password-change-required"));

        // but it can see who it is, and change the password
        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(post("/api/v1/auth/password/change").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD,
                                "newPassword", "a-replacement-password"))))
                .andExpect(status().isNoContent());

        // and afterwards the back office opens up
        Cookie[] fresh = signIn(adminEmail, "a-replacement-password");
        mockMvc.perform(get("/api/v1/admin/orders").cookie(fresh))
                .andExpect(status().isOk());
    }

    // ---- transitions ------------------------------------------------------

    @Test
    @DisplayName("an order can be moved along the legal path")
    void movesOrderThroughItsLifecycle() throws Exception {
        String orderNumber = placeOrder(1, "KV-BH-01");
        Cookie[] admin = adminSession();

        for (String next : List.of("CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED")) {
            mockMvc.perform(patch("/api/v1/admin/orders/" + orderNumber + "/status")
                            .cookie(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("status", next, "note", "moved to " + next))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.order.status").value(next));
        }

        mockMvc.perform(get("/api/v1/admin/orders/" + orderNumber).cookie(admin))
                .andExpect(status().isOk())
                // placement plus four moves
                .andExpect(jsonPath("$.order.timeline.length()").value(5))
                .andExpect(jsonPath("$.allowedStatuses").isEmpty());
    }

    @Test
    @DisplayName("an illegal transition is refused with a readable reason")
    void refusesIllegalTransitions() throws Exception {
        String orderNumber = placeOrder(1, "KV-BH-01");
        Cookie[] admin = adminSession();

        // NEW cannot jump straight to SHIPPED
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderNumber + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "SHIPPED"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://karvya.example/problems/illegal-status-transition"));

        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.NEW);
    }

    @Test
    @DisplayName("a shipped order cannot be cancelled")
    void cannotCancelAfterShipping() throws Exception {
        String orderNumber = placeOrder(1, "KV-BH-01");
        Cookie[] admin = adminSession();

        for (String next : List.of("CONFIRMED", "PROCESSING", "SHIPPED")) {
            mockMvc.perform(patch("/api/v1/admin/orders/" + orderNumber + "/status")
                            .cookie(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("status", next))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderNumber + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "CANCELLED"))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- returning stock --------------------------------------------------

    @Test
    @DisplayName("cancelling returns exactly what the order took")
    void cancellingRestoresStock() throws Exception {
        int before = stockOf("KV-BH-01");
        String orderNumber = placeOrder(3, "KV-BH-01");
        assertThat(stockOf("KV-BH-01")).isEqualTo(before - 3);

        Cookie[] admin = adminSession();
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderNumber + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "CANCELLED", "note", "customer changed their mind"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.stockRestoredAt").isNotEmpty());

        assertThat(stockOf("KV-BH-01")).isEqualTo(before);
    }

    /**
     * Two administrators cancelling the same order at the same moment, or one
     * of them double-clicking. Exactly one cancellation may take effect, and
     * the stock must come back exactly once - crediting it twice would have the
     * catalogue selling pieces that do not exist.
     */
    @Test
    @DisplayName("a double cancellation returns the stock only once")
    void concurrentCancellationRestoresStockOnce() throws Exception {
        int before = stockOf("KV-BH-02");
        String orderNumber = placeOrder(2, "KV-BH-02");
        assertThat(stockOf("KV-BH-02")).isEqualTo(before - 2);

        Cookie[] admin = adminSession();
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await();
                    return mockMvc.perform(patch("/api/v1/admin/orders/" + orderNumber + "/status")
                                    .cookie(admin).with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of("status", "CANCELLED"))))
                            .andReturn().getResponse().getStatus();
                }));
            }
            startTogether.countDown();

            int accepted = 0;
            for (Future<Integer> result : results) {
                if (result.get(60, TimeUnit.SECONDS) == 200) {
                    accepted++;
                }
            }

            assertThat(accepted)
                    .as("only one cancellation should be accepted")
                    .isEqualTo(1);
            assertThat(stockOf("KV-BH-02"))
                    .as("stock must come back exactly once, not once per attempt")
                    .isEqualTo(before);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- payments and notes -----------------------------------------------

    @Test
    @DisplayName("an offline payment is recorded and can settle the order")
    void recordsOfflinePayment() throws Exception {
        String orderNumber = placeOrder(1, "KV-BH-01");
        Cookie[] admin = adminSession();

        mockMvc.perform(post("/api/v1/admin/orders/" + orderNumber + "/payments")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "methodCode", "BANK_TRANSFER",
                                "reference", "UTR123456",
                                "amount", "1250.00",
                                "receivedOn", LocalDate.now().toString(),
                                "note", "cleared this morning",
                                "markAsPaid", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].reference").value("UTR123456"))
                .andExpect(jsonPath("$.order.paymentStatus").value("PAID_OFFLINE"));
    }

    @Test
    @DisplayName("payment cannot be recorded against a cancelled order")
    void refusesPaymentOnCancelledOrder() throws Exception {
        String orderNumber = placeOrder(1, "KV-BH-01");
        Cookie[] admin = adminSession();

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderNumber + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "CANCELLED"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/orders/" + orderNumber + "/payments")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("methodCode", "BANK_TRANSFER", "amount", "1250.00",
                                "receivedOn", LocalDate.now().toString(), "markAsPaid", true))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("internal notes are for the team and never reach the customer")
    void internalNotesStayInternal() throws Exception {
        Cookie[] shopper = customerSession();
        Long productId = products.findBySku("KV-BH-01").orElseThrow().getId();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("productId", productId, "quantity", 1)));
        body.put("deliveryName", "Shopper");
        body.put("deliveryPhone", "9876543210");
        body.put("addressLine1", "1 Coir Lane");
        body.put("city", "Kochi");
        body.put("state", "Kerala");
        body.put("postalCode", "682001");
        body.put("paymentMethodCode", "CASH_ON_DELIVERY");

        MvcResult placed = mockMvc.perform(post("/api/v1/orders").cookie(shopper).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString())
                .get("orderNumber").asText();

        Cookie[] admin = adminSession();
        mockMvc.perform(post("/api/v1/admin/orders/" + orderNumber + "/notes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("note", "Customer asked to deliver after 6pm"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalNotes").value(
                        org.hamcrest.Matchers.containsString("after 6pm")));

        // the customer's own view of the same order carries no trace of it
        String customerView = mockMvc.perform(
                        get("/api/v1/account/orders/" + orderNumber).cookie(shopper))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(customerView).doesNotContain("after 6pm");
        assertThat(customerView).doesNotContain("internalNotes");
    }

    // ---- listing and export -----------------------------------------------

    @Test
    @DisplayName("orders can be found by number, name or status")
    void searchesAndFilters() throws Exception {
        String orderNumber = placeOrder(1, "KV-BH-01");
        Cookie[] admin = adminSession();

        mockMvc.perform(get("/api/v1/admin/orders").cookie(admin).param("q", orderNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber").value(orderNumber));

        mockMvc.perform(get("/api/v1/admin/orders").cookie(admin).param("q", "Asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/v1/admin/orders").cookie(admin).param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.orderNumber == '" + orderNumber + "')]").isEmpty());
    }

    @Test
    @DisplayName("the CSV export neutralises spreadsheet formulas")
    void csvExportIsInjectionSafe() throws Exception {
        Long productId = products.findBySku("KV-BH-01").orElseThrow().getId();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("productId", productId, "quantity", 1)));
        // a name that a spreadsheet would happily execute
        body.put("deliveryName", "=1+1");
        body.put("deliveryPhone", "9876543210");
        body.put("addressLine1", "1 Coir Lane");
        body.put("city", "Kochi");
        body.put("state", "Kerala");
        body.put("postalCode", "682001");
        body.put("paymentMethodCode", "CASH_ON_DELIVERY");

        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated());

        Cookie[] admin = adminSession();
        String csv = mockMvc.perform(get("/api/v1/admin/orders/export.csv").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("Order number");
        assertThat(csv)
                .as("the formula must be defanged with a leading apostrophe")
                .contains("'=1+1")
                .doesNotContain(",=1+1,");
    }

    // ---- dashboard --------------------------------------------------------

    @Test
    @DisplayName("the dashboard reports counts, low stock and notification health")
    void buildsTheDashboard() throws Exception {
        placeOrder(1, "KV-BH-01");
        Cookie[] admin = adminSession();

        mockMvc.perform(get("/api/v1/admin/dashboard").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordersByStatus.NEW").isNumber())
                .andExpect(jsonPath("$.ordersByPaymentStatus.PENDING").isNumber())
                .andExpect(jsonPath("$.recentOrders").isArray())
                // KV-BH-04 is seeded at 2 against a threshold of 3
                .andExpect(jsonPath("$.lowStock[?(@.sku == 'KV-BH-04')]").exists())
                // the value figure must always say what period it covers
                .andExpect(jsonPath("$.orderValueWindow").value("Last 30 days"))
                .andExpect(jsonPath("$.orderValueInWindow").isNumber())
                .andExpect(jsonPath("$.pendingNotifications").isNumber());
    }
}
