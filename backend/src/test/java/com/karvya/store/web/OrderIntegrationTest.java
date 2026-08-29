package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.application.cart.dto.CartDtos;
import com.karvya.store.application.order.PlaceOrderService;
import com.karvya.store.application.order.dto.CheckoutRequest;
import com.karvya.store.domain.model.NotificationStatus;
import com.karvya.store.domain.model.OrderStatus;
import com.karvya.store.domain.model.PaymentStatus;
import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.repository.CustomerOrderRepository;
import com.karvya.store.domain.repository.EmailNotificationRepository;
import com.karvya.store.domain.repository.ProductRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

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
 * Checkout: the order transaction, stock reservation, and who may see an order.
 *
 * <p>Deliberately not annotated {@code @Transactional}. A test-managed
 * transaction would roll everything back and, worse, would hide the very
 * behaviour under examination here - concurrent transactions competing for the
 * same rows.
 */
class OrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ProductRepository products;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private EmailNotificationRepository notifications;
    @Autowired private PlaceOrderService placeOrderService;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final Map<String, Integer> stockToRestore = new LinkedHashMap<>();

    /** Puts back any stock a test consumed, so classes stay independent. */
    @AfterEach
    void restoreStock() {
        stockToRestore.forEach((sku, original) -> transactionTemplate.executeWithoutResult(status -> {
            Product product = products.findBySku(sku).orElseThrow();
            product.setStockQuantity(original);
            products.save(product);
        }));
        stockToRestore.clear();
    }

    private Product product(String sku) {
        Product product = products.findBySku(sku).orElseThrow();
        stockToRestore.putIfAbsent(sku, product.getStockQuantity());
        return product;
    }

    private void setStock(String sku, int quantity) {
        transactionTemplate.executeWithoutResult(status -> {
            Product product = products.findBySku(sku).orElseThrow();
            stockToRestore.putIfAbsent(sku, product.getStockQuantity());
            product.setStockQuantity(quantity);
            products.save(product);
        });
    }

    private Map<String, Object> checkoutBody(Long productId, int quantity, String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("productId", productId, "quantity", quantity)));
        body.put("deliveryName", "Asha Menon");
        body.put("deliveryPhone", "9876543210");
        if (email != null) body.put("deliveryEmail", email);
        body.put("addressLine1", "1 Coir Lane");
        body.put("city", "Kochi");
        body.put("state", "Kerala");
        body.put("postalCode", "682001");
        body.put("paymentMethodCode", "CASH_ON_DELIVERY");
        return body;
    }

    private CheckoutRequest checkoutRequest(Long productId, int quantity) {
        return new CheckoutRequest(
                List.of(new CartDtos.LineRequest(productId, quantity)),
                null, "Race Buyer", "9876543210", null,
                "1 Coir Lane", null, "Kochi", "Kerala", "682001",
                null, null, "CASH_ON_DELIVERY");
    }

    private Cookie[] registerAndLogin() throws Exception {
        String email = uniqueEmail("buyer");
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Buyer", "email", email,
                                "password", "a-long-enough-password"))))
                .andExpect(status().isCreated());
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "a-long-enough-password"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    // ---- placing an order -------------------------------------------------

    @Test
    @DisplayName("a guest can place an order, and stock moves by exactly that much")
    void guestCheckoutReservesStock() throws Exception {
        Product item = product("KV-BH-01");
        int before = item.getStockQuantity();

        MvcResult result = mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody(item.getId(), 2, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.detail.status").value("NEW"))
                .andExpect(jsonPath("$.detail.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.detail.lines[0].quantity").value(2))
                .andReturn();

        String orderNumber = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("orderNumber").asText();

        assertThat(orderNumber).matches("KV-\\d{6}-[0-9A-Z]{4}");
        assertThat(products.findBySku("KV-BH-01").orElseThrow().getStockQuantity())
                .isEqualTo(before - 2);
    }

    @Test
    @DisplayName("totals come from the catalogue, never from the request")
    void totalsAreComputedServerSide() throws Exception {
        Product item = product("KV-BH-01");

        Map<String, Object> tampered = checkoutBody(item.getId(), 2, null);
        tampered.put("subtotal", 1);
        tampered.put("total", 1);
        tampered.put("deliveryCharge", -500);

        double expected = item.getPrice().doubleValue() * 2;

        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(tampered)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detail.subtotal").value(expected))
                .andExpect(jsonPath("$.detail.total").value(expected));
    }

    @Test
    @DisplayName("the order line keeps the name and price as they were bought")
    void lineItemsAreSnapshots() throws Exception {
        Product item = product("KV-BH-02");
        String originalName = item.getName();

        MvcResult result = mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody(item.getId(), 1, null))))
                .andExpect(status().isCreated())
                .andReturn();

        String orderNumber = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("orderNumber").asText();

        // rename the product after the sale
        transactionTemplate.executeWithoutResult(status -> {
            Product p = products.findBySku("KV-BH-02").orElseThrow();
            p.setName("Renamed After The Sale");
            products.save(p);
        });

        try {
            var order = orders.findWithDetailByOrderNumber(orderNumber).orElseThrow();
            assertThat(order.getItems().get(0).getProductName()).isEqualTo(originalName);
        } finally {
            transactionTemplate.executeWithoutResult(status -> {
                Product p = products.findBySku("KV-BH-02").orElseThrow();
                p.setName(originalName);
                products.save(p);
            });
        }
    }

    @Test
    @DisplayName("ordering more than remains is refused, and says what changed")
    void refusesToOversell() throws Exception {
        Product item = product("KV-BH-03");
        setStock("KV-BH-03", 2);

        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody(item.getId(), 5, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("https://karvya.example/problems/checkout-cart-changed"))
                .andExpect(jsonPath("$.adjustments[0].kind").value("QUANTITY_REDUCED"));

        // nothing was taken
        assertThat(products.findBySku("KV-BH-03").orElseThrow().getStockQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("an unknown payment method is refused")
    void refusesUnknownPaymentMethod() throws Exception {
        Product item = product("KV-BH-01");
        Map<String, Object> body = checkoutBody(item.getId(), 1, null);
        body.put("paymentMethodCode", "BITCOIN");

        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://karvya.example/problems/payment-method-unavailable"));
    }

    @Test
    @DisplayName("an incomplete address is rejected before anything is written")
    void rejectsIncompleteAddress() throws Exception {
        Product item = product("KV-BH-01");
        Map<String, Object> body = checkoutBody(item.getId(), 1, null);
        body.remove("postalCode");

        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.postalCode").exists());
    }

    // ---- the oversell race ------------------------------------------------

    /**
     * The test this whole design exists for.
     *
     * <p>Ten buyers rush three units at the same instant. Exactly three orders
     * must exist, stock must land on zero, and it must never go negative.
     *
     * <p>Removing the pessimistic lock was tried, and the result is worth
     * recording because it is not the obvious one: stock is still never
     * oversold, because the {@code @Version} column on Product makes the
     * losing transactions fail. What breaks is throughput - only two of the
     * three units sell, and a real customer is turned away from stock that
     * exists. The row lock is what makes concurrent buyers queue instead of
     * collide, and this test fails either way, so it guards both properties.
     */
    @Test
    @DisplayName("concurrent checkouts cannot oversell the last few units")
    void concurrentCheckoutsCannotOversell() throws Exception {
        final int available = 3;
        final int buyers = 10;
        setStock("KV-BH-05", available);
        Long productId = products.findBySku("KV-BH-05").orElseThrow().getId();

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();

        try {
            for (int i = 0; i < buyers; i++) {
                attempts.add(pool.submit(() -> {
                    startTogether.await();
                    try {
                        placeOrderService.place(checkoutRequest(productId, 1), null);
                        return true;
                    } catch (Exception e) {
                        // sold out, or lost the race for the row: both are correct
                        return false;
                    }
                }));
            }

            startTogether.countDown();

            int succeeded = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(60, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }

            int remaining = products.findBySku("KV-BH-05").orElseThrow().getStockQuantity();

            assertThat(succeeded)
                    .as("exactly the available units should sell")
                    .isEqualTo(available);
            assertThat(remaining)
                    .as("stock must land on zero and never go negative")
                    .isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- who may see an order ---------------------------------------------

    @Test
    @DisplayName("the confirmation needs its token; the order number alone is not enough")
    void confirmationRequiresTheToken() throws Exception {
        Product item = product("KV-BH-01");

        MvcResult placed = mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody(item.getId(), 1, null))))
                .andExpect(status().isCreated())
                .andReturn();

        var body = objectMapper.readTree(placed.getResponse().getContentAsString());
        String orderNumber = body.get("orderNumber").asText();
        String token = body.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/orders/" + orderNumber).param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                // internal fields must never appear in a customer-facing view
                .andExpect(jsonPath("$.internalNotes").doesNotExist())
                .andExpect(jsonPath("$.accessTokenHash").doesNotExist());

        mockMvc.perform(get("/api/v1/orders/" + orderNumber))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/orders/" + orderNumber).param("token", "wrong-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a signed-in order joins that account and empties its cart")
    void signedInCheckoutLinksAccountAndClearsCart() throws Exception {
        Cookie[] buyer = registerAndLogin();
        Product item = product("KV-BH-01");

        mockMvc.perform(put("/api/v1/account/cart/items/" + item.getId())
                        .cookie(buyer).with(csrf()).param("quantity", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1));

        MvcResult placed = mockMvc.perform(post("/api/v1/orders")
                        .cookie(buyer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody(item.getId(), 1, null))))
                .andExpect(status().isCreated())
                .andReturn();

        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString())
                .get("orderNumber").asText();

        // the cart is emptied only after the order committed
        mockMvc.perform(get("/api/v1/account/cart").cookie(buyer))
                .andExpect(jsonPath("$.itemCount").value(0));

        mockMvc.perform(get("/api/v1/account/orders").cookie(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderNumber").value(orderNumber));

        mockMvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber));
    }

    @Test
    @DisplayName("one customer cannot read another's order")
    void ordersAreIsolatedBetweenCustomers() throws Exception {
        Cookie[] buyer = registerAndLogin();
        Cookie[] stranger = registerAndLogin();
        Product item = product("KV-BH-01");

        MvcResult placed = mockMvc.perform(post("/api/v1/orders")
                        .cookie(buyer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody(item.getId(), 1, null))))
                .andExpect(status().isCreated())
                .andReturn();

        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString())
                .get("orderNumber").asText();

        mockMvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(stranger))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/account/orders").cookie(stranger))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---- notifications ----------------------------------------------------

    @Test
    @DisplayName("an order queues its emails rather than sending them inline")
    void queuesNotificationsInTheOutbox() throws Exception {
        Product item = product("KV-BH-01");
        long before = notifications.count();

        MvcResult placed = mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody(item.getId(), 1, "buyer@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detail.confirmationEmailQueued").value(true))
                .andReturn();

        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString())
                .get("orderNumber").asText();

        // one for the administrator, one for the customer
        assertThat(notifications.count() - before).isEqualTo(2);

        var queued = notifications.findAll().stream()
                .filter(n -> n.getPayload().contains(orderNumber))
                .toList();
        assertThat(queued).allMatch(n -> n.getStatus() == NotificationStatus.PENDING);

        // and crucially, the order exists regardless of whether they ever send
        assertThat(orders.findByOrderNumber(orderNumber)).isPresent();
    }

    @Test
    @DisplayName("no customer email means no customer notification, and no pretence of one")
    void withoutAnEmailNoCustomerNotificationIsQueued() throws Exception {
        Product item = product("KV-BH-01");
        long before = notifications.count();

        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody(item.getId(), 1, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detail.confirmationEmailQueued").value(false));

        // only the administrator alert
        assertThat(notifications.count() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("the opening status is on the timeline")
    void recordsPlacementInTheTimeline() throws Exception {
        Product item = product("KV-BH-01");

        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody(item.getId(), 1, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detail.timeline[0].field").value("STATUS"))
                .andExpect(jsonPath("$.detail.timeline[0].to").value(OrderStatus.NEW.name()))
                .andExpect(jsonPath("$.detail.paymentStatus").value(PaymentStatus.PENDING.name()));
    }
}
