package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.model.Role;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.CategoryRepository;
import com.karvya.store.domain.repository.ProductRepository;
import com.karvya.store.domain.repository.RoleRepository;
import com.karvya.store.domain.repository.VendorRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suppliers, and the line that must not be crossed.
 *
 * <p>The price paid to a supplier is the shop's margin and their contact
 * details are not a shopper's business. Most of this file is about proving
 * neither reaches the storefront.
 */
class VendorIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private VendorRepository vendors;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "an-admin-password-1";
    private static final String SECRET_PHONE = "9000011111";
    private static final String SECRET_PRICE = "410.00";

    private String adminEmail;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        adminEmail = uniqueEmail("vendoradmin");
        transactionTemplate.executeWithoutResult(status -> {
            AppUser admin = AppUser.create(adminEmail, "Vendor Admin",
                    passwordEncoder.encode(PASSWORD), null);
            admin.addRole(roles.findByCode(Role.ADMIN).orElseThrow());
            users.save(admin);
        });
        categoryId = categories.findBySlug("bird-houses-and-nests").orElseThrow().getId();
    }

    @AfterEach
    void detachVendors() {
        // the seeded catalogue is shared; leave no product pointing at a
        // supplier this test invented
        transactionTemplate.executeWithoutResult(status ->
                products.findAll().forEach(product -> {
                    if (product.getVendor() != null) {
                        product.setVendor(null);
                        product.setVendorPrice(null);
                        products.save(product);
                    }
                }));
        vendors.deleteAll();
    }

    private Cookie[] adminSession() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", adminEmail, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    private Map<String, Object> vendorBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("contactName", "Sujatha");
        body.put("email", "sujatha@supplier.example");
        body.put("phone", SECRET_PHONE);
        body.put("address", "12 Weavers Lane, Alappuzha");
        body.put("deliveryTime", "2 to 3 weeks");
        body.put("conditions", "Half on order, half on delivery.");
        body.put("active", true);
        return body;
    }

    private long createVendor(Cookie[] admin, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/vendors").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(vendorBody(name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    /** Attaches a supplier to a seeded product and returns that product's slug. */
    private String attachVendorTo(Cookie[] admin, String sku, long vendorId) throws Exception {
        Product product = products.findBySku(sku).orElseThrow();

        MvcResult current = mockMvc.perform(
                        get("/api/v1/admin/products/" + product.getId()).cookie(admin))
                .andExpect(status().isOk()).andReturn();
        var node = objectMapper.readTree(current.getResponse().getContentAsString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", node.get("sku").asText());
        body.put("name", node.get("name").asText());
        body.put("slug", node.get("slug").asText());
        body.put("categoryId", categoryId);
        body.put("price", node.get("price").asText());
        body.put("stockQuantity", node.get("stockQuantity").asInt());
        body.put("lowStockThreshold", node.get("lowStockThreshold").asInt());
        body.put("featured", node.get("featured").asBoolean());
        body.put("status", node.get("status").asText());
        body.put("placeholderContent", node.get("placeholderContent").asBoolean());
        body.put("version", node.get("version").asLong());
        body.put("vendorId", vendorId);
        body.put("vendorPrice", SECRET_PRICE);

        mockMvc.perform(put("/api/v1/admin/products/" + product.getId()).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value((int) vendorId))
                .andExpect(jsonPath("$.vendorPrice").value(410.00));

        return node.get("slug").asText();
    }

    // ---- the line that must not be crossed ---------------------------------

    @Test
    @DisplayName("a supplier never appears on the public product page")
    void neverLeaksToTheStorefront() throws Exception {
        Cookie[] admin = adminSession();
        long vendorId = createVendor(admin, "Alappuzha Coir Works");
        String slug = attachVendorTo(admin, "KV-BH-01", vendorId);

        String detail = mockMvc.perform(get("/api/v1/products/" + slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(detail).doesNotContain("Alappuzha Coir Works");
        assertThat(detail).doesNotContain(SECRET_PHONE);
        assertThat(detail).doesNotContain(SECRET_PRICE);
        assertThat(detail).doesNotContain("sujatha@supplier.example");
        assertThat(detail).doesNotContain("vendor");

        String listing = mockMvc.perform(get("/api/v1/products?size=20"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(listing).doesNotContain("Alappuzha Coir Works");
        assertThat(listing).doesNotContain(SECRET_PRICE);
    }

    @Test
    @DisplayName("a customer cannot read the supplier list at all")
    void vendorsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/vendors").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(vendorBody("Sneaky"))))
                .andExpect(status().isUnauthorized());
    }

    // ---- what the back office needs -----------------------------------------

    @Test
    @DisplayName("an order shows who to reorder each line from")
    void orderShowsTheSupplier() throws Exception {
        Cookie[] admin = adminSession();
        long vendorId = createVendor(admin, "Alappuzha Coir Works");
        attachVendorTo(admin, "KV-BH-01", vendorId);

        Long productId = products.findBySku("KV-BH-01").orElseThrow().getId();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("items", List.of(Map.of("productId", productId, "quantity", 1)));
        order.put("deliveryName", "Asha Menon");
        order.put("deliveryPhone", "9876543210");
        order.put("addressLine1", "1 Coir Lane");
        order.put("city", "Kochi");
        order.put("state", "Kerala");
        order.put("postalCode", "682001");
        order.put("paymentMethodCode", "CASH_ON_DELIVERY");

        MvcResult placed = mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(order)))
                .andExpect(status().isCreated())
                .andReturn();
        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString())
                .get("orderNumber").asText();

        mockMvc.perform(get("/api/v1/admin/orders/" + orderNumber).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supply[0].vendorName").value("Alappuzha Coir Works"))
                .andExpect(jsonPath("$.supply[0].phone").value(SECRET_PHONE))
                .andExpect(jsonPath("$.supply[0].deliveryTime").value("2 to 3 weeks"))
                .andExpect(jsonPath("$.supply[0].conditions").value("Half on order, half on delivery."))
                .andExpect(jsonPath("$.supply[0].vendorPrice").value(410.00))
                .andExpect(jsonPath("$.supply[0].productSku").value("KV-BH-01"))
                .andExpect(jsonPath("$.supply[0].quantity").value(1));
    }

    @Test
    @DisplayName("a line with no supplier is still listed, blank rather than missing")
    void unsuppliedLineStillAppears() throws Exception {
        Cookie[] admin = adminSession();

        Long productId = products.findBySku("KV-BH-03").orElseThrow().getId();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("items", List.of(Map.of("productId", productId, "quantity", 1)));
        order.put("deliveryName", "Asha Menon");
        order.put("deliveryPhone", "9876543210");
        order.put("addressLine1", "1 Coir Lane");
        order.put("city", "Kochi");
        order.put("state", "Kerala");
        order.put("postalCode", "682001");
        order.put("paymentMethodCode", "CASH_ON_DELIVERY");

        MvcResult placed = mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(order)))
                .andExpect(status().isCreated()).andReturn();
        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString())
                .get("orderNumber").asText();

        mockMvc.perform(get("/api/v1/admin/orders/" + orderNumber).cookie(admin))
                .andExpect(jsonPath("$.supply.length()").value(1))
                .andExpect(jsonPath("$.supply[0].productSku").value("KV-BH-03"))
                .andExpect(jsonPath("$.supply[0].vendorName").doesNotExist());
    }

    /**
     * The foreign key would set the products' vendor to null, so the deletion
     * would look like it worked while quietly erasing where those pieces come
     * from.
     */
    @Test
    @DisplayName("a supplier still sourcing products cannot be deleted")
    void refusesToDeleteASupplierInUse() throws Exception {
        Cookie[] admin = adminSession();
        long vendorId = createVendor(admin, "Alappuzha Coir Works");
        attachVendorTo(admin, "KV-BH-01", vendorId);

        mockMvc.perform(delete("/api/v1/admin/vendors/" + vendorId).cookie(admin).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://karvya.example/problems/vendor-in-use"));

        assertThat(vendors.findById(vendorId)).isPresent();
    }

    @Test
    @DisplayName("an unused supplier can be removed")
    void removesAnUnusedSupplier() throws Exception {
        Cookie[] admin = adminSession();
        long vendorId = createVendor(admin, "Unused Supplier");

        mockMvc.perform(delete("/api/v1/admin/vendors/" + vendorId).cookie(admin).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(vendors.findById(vendorId)).isEmpty();
    }

    @Test
    @DisplayName("the list reports how many products each supplies")
    void listsProductCounts() throws Exception {
        Cookie[] admin = adminSession();
        long vendorId = createVendor(admin, "Alappuzha Coir Works");
        attachVendorTo(admin, "KV-BH-01", vendorId);

        mockMvc.perform(get("/api/v1/admin/vendors").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Alappuzha Coir Works')].productCount").value(1));
    }
}
