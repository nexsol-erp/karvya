package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.domain.model.CustomerAddress;
import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.repository.CustomerAddressRepository;
import com.karvya.store.domain.repository.ProductRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A signed-in customer should be asked for their address once.
 *
 * <p>The address typed at checkout is kept against the account and made its
 * default, so the next order offers it already selected.
 */
class CheckoutAddressIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CustomerAddressRepository addresses;
    @Autowired private ProductRepository products;

    private Long productId() {
        return products.findBySku("KV-BH-01").map(Product::getId).orElseThrow();
    }

    private Cookie[] registerAndLogin() throws Exception {
        String email = uniqueEmail("addressbuyer");
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Asha Menon", "email", email,
                                "password", "a-long-enough-password"))))
                .andExpect(status().isCreated());
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "a-long-enough-password"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    private Map<String, Object> checkoutBody(String line1, String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("productId", productId(), "quantity", 1)));
        body.put("deliveryName", "Asha Menon");
        body.put("deliveryPhone", phone);
        body.put("addressLine1", line1);
        body.put("city", "Kochi");
        body.put("state", "Kerala");
        body.put("postalCode", "682001");
        body.put("paymentMethodCode", "CASH_ON_DELIVERY");
        return body;
    }

    private void order(Cookie[] session, Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/v1/orders").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated());
    }

    private Long userIdOf(Cookie[] session) throws Exception {
        String body = mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    @DisplayName("the address typed at checkout is kept for next time")
    void keepsTheTypedAddress() throws Exception {
        Cookie[] buyer = registerAndLogin();

        order(buyer, checkoutBody("1 Coir Lane", "9876543210"));

        List<CustomerAddress> saved = addresses.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(
                userIdOf(buyer));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getLine1()).isEqualTo("1 Coir Lane");
        assertThat(saved.get(0).getCity()).isEqualTo("Kochi");
        assertThat(saved.get(0).getRecipientName()).isEqualTo("Asha Menon");
        // default, so the next checkout offers it without asking
        assertThat(saved.get(0).isDefaultAddress()).isTrue();
    }

    @Test
    @DisplayName("the saved address is offered back through the account API")
    void offersItBackOnTheNextVisit() throws Exception {
        Cookie[] buyer = registerAndLogin();
        order(buyer, checkoutBody("1 Coir Lane", "9876543210"));

        mockMvc.perform(get("/api/v1/account/addresses").cookie(buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].line1").value("1 Coir Lane"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    @DisplayName("ordering to the same place twice does not store it twice")
    void doesNotDuplicate() throws Exception {
        Cookie[] buyer = registerAndLogin();

        order(buyer, checkoutBody("1 Coir Lane", "9876543210"));
        // the same place, typed differently: spacing, case, and separators in
        // the number. A dialling code is deliberately not treated as noise -
        // deciding that "+91 98765 43210" is the same subscriber as
        // "9876543210" means assuming a country, which nothing here is told.
        order(buyer, checkoutBody("1  coir LANE ", "98765-43210"));

        assertThat(addresses.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userIdOf(buyer)))
                .hasSize(1);
    }

    @Test
    @DisplayName("a different address is added, and becomes the one offered next")
    void mostRecentBecomesTheDefault() throws Exception {
        Cookie[] buyer = registerAndLogin();

        order(buyer, checkoutBody("1 Coir Lane", "9876543210"));
        order(buyer, checkoutBody("22 Backwater Road", "9876543210"));

        List<CustomerAddress> saved = addresses.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(
                userIdOf(buyer));

        assertThat(saved).hasSize(2);
        // exactly one default - the partial unique index depends on it
        assertThat(saved.stream().filter(CustomerAddress::isDefaultAddress)).hasSize(1);
        assertThat(saved.get(0).getLine1()).isEqualTo("22 Backwater Road");
        assertThat(saved.get(0).isDefaultAddress()).isTrue();
    }

    @Test
    @DisplayName("a guest order stores no address, because there is no account")
    void guestsAreNotRemembered() throws Exception {
        long before = addresses.count();

        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(checkoutBody("9 Guest Street", "9876543210"))))
                .andExpect(status().isCreated());

        assertThat(addresses.count()).isEqualTo(before);
    }
}
