package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.application.settings.SettingsService;
import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.repository.ProductRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cart pricing, stock capping and the guest-cart merge.
 *
 * <p>The recurring theme: the browser sends identifiers and quantities, and
 * every figure comes back from the server. These tests assert that a cart the
 * catalogue cannot honour is corrected, and that the correction is reported
 * rather than applied quietly.
 */
class CartIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository products;

    @Autowired
    private SettingsService settings;

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** KV-BH-04 is seeded with a stock of 2, which makes it the cap fixture. */
    private Product scarceProduct() {
        return products.findBySku("KV-BH-04").orElseThrow();
    }

    private Product plentifulProduct() {
        return products.findBySku("KV-BH-01").orElseThrow();
    }

    @AfterEach
    void resetDeliverySettings() {
        settings.put(SettingsService.DELIVERY_CHARGE, "0.00", "test");
        settings.put(SettingsService.FREE_DELIVERY_THRESHOLD, null, "test");
    }

    private String cart(Object... productIdQuantityPairs) throws Exception {
        var items = new java.util.ArrayList<Map<String, Object>>();
        for (int i = 0; i < productIdQuantityPairs.length; i += 2) {
            items.add(Map.of(
                    "productId", productIdQuantityPairs[i],
                    "quantity", productIdQuantityPairs[i + 1]));
        }
        return json(Map.of("items", items));
    }

    private Cookie[] registerAndLogin() throws Exception {
        String email = uniqueEmail("shopper");
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Cart Shopper", "email", email,
                                "password", "a-long-enough-password"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "a-long-enough-password"))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookies();
    }

    // ---- visitor cart -----------------------------------------------------

    @Test
    @DisplayName("prices a visitor cart from the catalogue, not from the request")
    void pricesVisitorCart() throws Exception {
        Product product = plentifulProduct();

        mockMvc.perform(post("/api/v1/cart/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cart(product.getId(), 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPrice").value(product.getPrice().doubleValue()))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].lineTotal")
                        .value(product.getPrice().multiply(java.math.BigDecimal.valueOf(2)).doubleValue()))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.adjustments").isEmpty());
    }

    @Test
    @DisplayName("a price sent by the browser is ignored entirely")
    void ignoresBrowserSuppliedPrice() throws Exception {
        Product product = plentifulProduct();

        String tampered = json(Map.of("items", List.of(Map.of(
                "productId", product.getId(),
                "quantity", 1,
                "unitPrice", 1,
                "lineTotal", 1,
                "price", 1))));

        mockMvc.perform(post("/api/v1/cart/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tampered))
                .andExpect(status().isOk())
                // the catalogue price stands, not the one that was posted
                .andExpect(jsonPath("$.lines[0].unitPrice").value(product.getPrice().doubleValue()))
                .andExpect(jsonPath("$.total").value(product.getPrice().doubleValue()));
    }

    @Test
    @DisplayName("a quantity beyond stock is reduced and the reduction is reported")
    void capsQuantityAtAvailableStock() throws Exception {
        Product scarce = scarceProduct();
        int stock = scarce.getStockQuantity();

        mockMvc.perform(post("/api/v1/cart/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cart(scarce.getId(), stock + 40)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(stock))
                .andExpect(jsonPath("$.adjustments[0].kind").value("QUANTITY_REDUCED"))
                .andExpect(jsonPath("$.adjustments[0].productId").value(scarce.getId()));
    }

    @Test
    @DisplayName("an unknown product is dropped rather than failing the whole cart")
    void dropsUnknownProduct() throws Exception {
        Product good = plentifulProduct();

        mockMvc.perform(post("/api/v1/cart/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cart(good.getId(), 1, 999_999L, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].productId").value(good.getId()))
                .andExpect(jsonPath("$.adjustments[0].kind").value("REMOVED_UNAVAILABLE"));
    }

    @Test
    @DisplayName("the same product sent twice becomes one line with the quantities summed")
    void mergesDuplicateLines() throws Exception {
        Product product = plentifulProduct();

        mockMvc.perform(post("/api/v1/cart/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cart(product.getId(), 2, product.getId(), 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(5));
    }

    @Test
    @DisplayName("an empty cart costs nothing, delivery included")
    void emptyCartHasNoDeliveryCharge() throws Exception {
        settings.put(SettingsService.DELIVERY_CHARGE, "80.00", "test");

        mockMvc.perform(post("/api/v1/cart/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("items", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(0))
                .andExpect(jsonPath("$.deliveryCharge").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("delivery is charged, then waived once the threshold is met")
    void appliesDeliveryChargeAndFreeThreshold() throws Exception {
        Product product = plentifulProduct();
        settings.put(SettingsService.DELIVERY_CHARGE, "80.00", "test");
        settings.put(SettingsService.FREE_DELIVERY_THRESHOLD, "2000.00", "test");

        // one unit at 1250 is below the threshold
        mockMvc.perform(post("/api/v1/cart/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cart(product.getId(), 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryCharge").value(80.00))
                .andExpect(jsonPath("$.total").value(1330.00))
                .andExpect(jsonPath("$.amountToFreeDelivery").value(750.00));

        // two units clear it, so delivery is waived
        mockMvc.perform(post("/api/v1/cart/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cart(product.getId(), 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryCharge").value(0))
                .andExpect(jsonPath("$.total").value(2500.00))
                .andExpect(jsonPath("$.amountToFreeDelivery").doesNotExist());
    }

    // ---- signed-in cart ---------------------------------------------------

    @Test
    @DisplayName("the saved cart survives across requests and is owner-scoped")
    void keepsAndIsolatesTheSavedCart() throws Exception {
        Cookie[] shopper = registerAndLogin();
        Cookie[] other = registerAndLogin();
        Product product = plentifulProduct();

        mockMvc.perform(put("/api/v1/account/cart/items/" + product.getId())
                        .cookie(shopper).with(csrf()).param("quantity", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(3));

        mockMvc.perform(get("/api/v1/account/cart").cookie(shopper))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].productId").value(product.getId()))
                .andExpect(jsonPath("$.itemCount").value(3));

        // a different account sees its own empty cart, not this one
        mockMvc.perform(get("/api/v1/account/cart").cookie(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isEmpty())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    @DisplayName("setting a quantity of zero removes the line")
    void zeroQuantityRemovesLine() throws Exception {
        Cookie[] shopper = registerAndLogin();
        Product product = plentifulProduct();

        mockMvc.perform(put("/api/v1/account/cart/items/" + product.getId())
                        .cookie(shopper).with(csrf()).param("quantity", "2"))
                .andExpect(jsonPath("$.lines.length()").value(1));

        mockMvc.perform(put("/api/v1/account/cart/items/" + product.getId())
                        .cookie(shopper).with(csrf()).param("quantity", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    @DisplayName("the saved cart cannot hold more than the catalogue has")
    void savedCartCapsAtStock() throws Exception {
        Cookie[] shopper = registerAndLogin();
        Product scarce = scarceProduct();

        mockMvc.perform(put("/api/v1/account/cart/items/" + scarce.getId())
                        .cookie(shopper).with(csrf()).param("quantity", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(scarce.getStockQuantity()));
    }

    @Test
    @DisplayName("an anonymous visitor has no saved cart to read")
    void anonymousCannotReadASavedCart() throws Exception {
        mockMvc.perform(get("/api/v1/account/cart"))
                .andExpect(status().isUnauthorized());
    }

    // ---- the merge --------------------------------------------------------

    @Test
    @DisplayName("merging sums the same product rather than overwriting it")
    void mergeSumsQuantitiesForTheSameProduct() throws Exception {
        Cookie[] shopper = registerAndLogin();
        Product product = plentifulProduct();

        mockMvc.perform(put("/api/v1/account/cart/items/" + product.getId())
                        .cookie(shopper).with(csrf()).param("quantity", "2"))
                .andExpect(jsonPath("$.itemCount").value(2));

        // the browser cart held one more of the same piece
        mockMvc.perform(post("/api/v1/account/cart/merge")
                        .cookie(shopper).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cart(product.getId(), 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(3));
    }

    @Test
    @DisplayName("merging never produces a cart larger than available stock")
    void mergeCapsCombinedQuantityAtStock() throws Exception {
        Cookie[] shopper = registerAndLogin();
        Product scarce = scarceProduct();
        int stock = scarce.getStockQuantity();

        mockMvc.perform(put("/api/v1/account/cart/items/" + scarce.getId())
                        .cookie(shopper).with(csrf()).param("quantity", String.valueOf(stock)))
                .andExpect(jsonPath("$.lines[0].quantity").value(stock));

        // the guest cart holds the same amount again; summing would double it
        MvcResult merged = mockMvc.perform(post("/api/v1/account/cart/merge")
                        .cookie(shopper).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cart(scarce.getId(), stock)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(stock))
                .andReturn();

        int quantity = objectMapper.readTree(merged.getResponse().getContentAsString())
                .get("lines").get(0).get("quantity").asInt();
        assertThat(quantity).isLessThanOrEqualTo(stock);
    }

    @Test
    @DisplayName("merging brings in products the account cart did not have")
    void mergeAddsNewProducts() throws Exception {
        Cookie[] shopper = registerAndLogin();
        Product existing = plentifulProduct();
        Product incoming = products.findBySku("KV-BH-02").orElseThrow();

        mockMvc.perform(put("/api/v1/account/cart/items/" + existing.getId())
                        .cookie(shopper).with(csrf()).param("quantity", "1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/account/cart/merge")
                        .cookie(shopper).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cart(incoming.getId(), 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.itemCount").value(3));
    }

    @Test
    @DisplayName("merging an empty browser cart leaves the saved cart alone")
    void mergeWithEmptyGuestCartIsHarmless() throws Exception {
        Cookie[] shopper = registerAndLogin();
        Product product = plentifulProduct();

        mockMvc.perform(put("/api/v1/account/cart/items/" + product.getId())
                        .cookie(shopper).with(csrf()).param("quantity", "4"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/account/cart/merge")
                        .cookie(shopper).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("items", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(4));
    }

    @Test
    @DisplayName("emptying the cart clears every line")
    void clearsTheCart() throws Exception {
        Cookie[] shopper = registerAndLogin();
        Product product = plentifulProduct();

        mockMvc.perform(put("/api/v1/account/cart/items/" + product.getId())
                        .cookie(shopper).with(csrf()).param("quantity", "2"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/account/cart").cookie(shopper).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isEmpty())
                .andExpect(jsonPath("$.itemCount").value(0));
    }
}
