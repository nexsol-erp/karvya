package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.Role;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.CategoryRepository;
import com.karvya.store.domain.repository.ProductRepository;
import com.karvya.store.domain.repository.RoleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Catalogue management and the contact form. */
class AdminCatalogueIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "an-admin-password-1";
    private String adminEmail;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        adminEmail = uniqueEmail("catadmin");
        transactionTemplate.executeWithoutResult(status -> {
            AppUser admin = AppUser.create(adminEmail, "Catalogue Admin",
                    passwordEncoder.encode(PASSWORD), null);
            admin.addRole(roles.findByCode(Role.ADMIN).orElseThrow());
            users.save(admin);
        });
        categoryId = categories.findBySlug("bird-houses-and-nests").orElseThrow().getId();
    }

    private Cookie[] adminSession() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", adminEmail, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    private Map<String, Object> productBody(String sku, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", sku);
        body.put("name", name);
        body.put("categoryId", categoryId);
        body.put("price", "1500.00");
        body.put("stockQuantity", 5);
        body.put("lowStockThreshold", 2);
        body.put("featured", false);
        body.put("status", "DRAFT");
        body.put("placeholderContent", false);
        return body;
    }

    private byte[] realPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(163, 59, 46));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    // ---- products ---------------------------------------------------------

    @Test
    @DisplayName("a product can be created and its slug derived from the name")
    void createsProductWithDerivedSlug() throws Exception {
        Cookie[] admin = adminSession();

        mockMvc.perform(post("/api/v1/admin/products").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("KV-NEW-01", "Woven Coir Lantern Shade"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("woven-coir-lantern-shade"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("a duplicate SKU is refused")
    void refusesDuplicateSku() throws Exception {
        Cookie[] admin = adminSession();

        mockMvc.perform(post("/api/v1/admin/products").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("KV-BH-01", "Clashing Product"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://karvya.example/problems/sku-in-use"));
    }

    /**
     * Two administrators editing the same product. The second save carries a
     * version that is no longer current and must be refused rather than
     * silently discarding the first one's work.
     */
    @Test
    @DisplayName("a stale edit is refused instead of overwriting someone else")
    void refusesStaleEdit() throws Exception {
        Cookie[] admin = adminSession();

        MvcResult created = mockMvc.perform(post("/api/v1/admin/products").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("KV-LOCK-01", "Version Test Piece"))))
                .andExpect(status().isCreated())
                .andReturn();

        var node = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = node.get("id").asLong();
        long staleVersion = node.get("version").asLong();

        Map<String, Object> firstEdit = productBody("KV-LOCK-01", "Renamed By First Editor");
        firstEdit.put("version", staleVersion);
        mockMvc.perform(put("/api/v1/admin/products/" + id).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(firstEdit)))
                .andExpect(status().isOk());

        // the second editor still holds the version they loaded
        Map<String, Object> secondEdit = productBody("KV-LOCK-01", "Renamed By Second Editor");
        secondEdit.put("version", staleVersion);
        mockMvc.perform(put("/api/v1/admin/products/" + id).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(secondEdit)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://karvya.example/problems/product-modified"));

        assertThat(products.findById(id).orElseThrow().getName())
                .isEqualTo("Renamed By First Editor");
    }

    @Test
    @DisplayName("a featured product must be unfeatured before it can be archived")
    void refusesArchivingAFeaturedProduct() throws Exception {
        Cookie[] admin = adminSession();
        Long featuredId = products.findBySku("KV-BH-01").orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/admin/products/" + featuredId + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ARCHIVED"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://karvya.example/problems/featured-product-archived"));
    }

    @Test
    @DisplayName("a product that has been sold is flagged as such before archiving")
    void reportsWhetherAProductHasBeenOrdered() throws Exception {
        Cookie[] admin = adminSession();
        Long soldId = products.findBySku("KV-BH-01").orElseThrow().getId();

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("items", List.of(Map.of("productId", soldId, "quantity", 1)));
        order.put("deliveryName", "Asha Menon");
        order.put("deliveryPhone", "9876543210");
        order.put("addressLine1", "1 Coir Lane");
        order.put("city", "Kochi");
        order.put("state", "Kerala");
        order.put("postalCode", "682001");
        order.put("paymentMethodCode", "CASH_ON_DELIVERY");

        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(order)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/products/" + soldId + "/usage").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBeenOrdered").value(true));
    }

    // ---- images -----------------------------------------------------------

    @Test
    @DisplayName("a photograph is stored under a generated name and leads the gallery")
    void uploadsAndOrdersImages() throws Exception {
        Cookie[] admin = adminSession();

        MvcResult created = mockMvc.perform(post("/api/v1/admin/products").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("KV-IMG-01", "Image Test Piece"))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(multipart("/api/v1/admin/products/" + id + "/images")
                            .file(new MockMultipartFile("file", "shot" + i + ".png",
                                    "image/png", realPng(600, 700)))
                            .file(new MockMultipartFile("altText", "", "text/plain",
                                    ("View " + i).getBytes(StandardCharsets.UTF_8)))
                            .cookie(admin).with(csrf()))
                    .andExpect(status().isCreated());
        }

        MvcResult withImages = mockMvc.perform(get("/api/v1/admin/products/" + id).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(2))
                // the client never chooses the filename
                .andExpect(jsonPath("$.images[0].storageKey")
                        .value(org.hamcrest.Matchers.matchesRegex("products/kv-img-01/[0-9a-f-]{36}\\.png")))
                .andExpect(jsonPath("$.images[0].primary").value(true))
                .andExpect(jsonPath("$.images[0].width").value(600))
                .andReturn();

        var images = objectMapper.readTree(withImages.getResponse().getContentAsString()).get("images");
        long first = images.get(0).get("id").asLong();
        long second = images.get(1).get("id").asLong();

        // promote the second photograph and reverse the order
        mockMvc.perform(put("/api/v1/admin/products/" + id + "/images/order")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("imageIds", List.of(second, first),
                                "primaryImageId", second))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].id").value(second))
                .andExpect(jsonPath("$.images[0].primary").value(true))
                .andExpect(jsonPath("$.images[1].primary").value(false));
    }

    @Test
    @DisplayName("a disguised script is refused at the upload endpoint")
    void refusesDisguisedUpload() throws Exception {
        Cookie[] admin = adminSession();
        Long id = products.findBySku("KV-BH-01").orElseThrow().getId();

        mockMvc.perform(multipart("/api/v1/admin/products/" + id + "/images")
                        .file(new MockMultipartFile("file", "evil.png", "image/png",
                                "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8)))
                        .cookie(admin).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- enquiries --------------------------------------------------------

    @Test
    @DisplayName("a contact message is saved and queued for the administrator")
    void acceptsAContactMessage() throws Exception {
        mockMvc.perform(post("/api/v1/enquiries").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Priya R",
                                "email", "priya@example.com",
                                "subject", "Custom size",
                                "message", "Could you make a larger version of the twin entrance house?"))))
                .andExpect(status().isAccepted());

        Cookie[] admin = adminSession();
        mockMvc.perform(get("/api/v1/admin/enquiries").cookie(admin).param("q", "Priya"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subject").value("Custom size"))
                .andExpect(jsonPath("$.content[0].status").value("NEW"));
    }

    /**
     * A filled honeypot is discarded, but answered exactly like a real
     * submission - telling a bot it was spotted only teaches it to adapt.
     */
    @Test
    @DisplayName("a honeypot submission is discarded, and looks identical from outside")
    void discardsHoneypotSubmissions() throws Exception {
        String marker = "honeypot-" + System.nanoTime();

        mockMvc.perform(post("/api/v1/enquiries").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Spam Bot", "email", "bot@example.com",
                                "subject", marker, "message", "buy things",
                                "website", "http://spam.example"))))
                .andExpect(status().isAccepted());

        Cookie[] admin = adminSession();
        mockMvc.perform(get("/api/v1/admin/enquiries").cookie(admin).param("q", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("an enquiry can be triaged with an internal note")
    void triagesAnEnquiry() throws Exception {
        mockMvc.perform(post("/api/v1/enquiries").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Triage Test", "email", "triage@example.com",
                                "subject", "A question", "message", "How long is delivery?"))))
                .andExpect(status().isAccepted());

        Cookie[] admin = adminSession();
        MvcResult listed = mockMvc.perform(get("/api/v1/admin/enquiries").cookie(admin)
                        .param("q", "triage@example.com"))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(listed.getResponse().getContentAsString())
                .get("content").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/v1/admin/enquiries/" + id + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "RESOLVED",
                                "internalNote", "Replied by WhatsApp"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.internalNote").value("Replied by WhatsApp"))
                .andExpect(jsonPath("$.handledBy").value(adminEmail));
    }

    @Test
    @DisplayName("the contact form validates before saving anything")
    void validatesTheContactForm() throws Exception {
        mockMvc.perform(post("/api/v1/enquiries").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "", "email", "not-an-email",
                                "subject", "", "message", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.message").exists());
    }

    @Test
    @DisplayName("enquiries are not readable without an admin session")
    void enquiriesAreNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/admin/enquiries"))
                .andExpect(status().isUnauthorized());
    }
}
