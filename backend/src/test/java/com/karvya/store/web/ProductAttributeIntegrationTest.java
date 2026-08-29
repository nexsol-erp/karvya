package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.Role;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.ProductAttributeRepository;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Selling a second kind of thing without changing the code.
 *
 * <p>This is the question the design exists to answer: a shop that sells coir
 * bird houses decides to sell books too, and needs to record an author and an
 * ISBN without a migration or a deployment.
 */
class ProductAttributeIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AppUserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProductAttributeRepository attributes;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final String PASSWORD = "an-admin-password-1";
    private String adminEmail;

    @BeforeEach
    void createAdmin() {
        adminEmail = uniqueEmail("attradmin");
        transactionTemplate.executeWithoutResult(status -> {
            AppUser admin = AppUser.create(adminEmail, "Attribute Admin",
                    passwordEncoder.encode(PASSWORD), null);
            admin.addRole(roles.findByCode(Role.ADMIN).orElseThrow());
            users.save(admin);
        });
    }

    private Cookie[] adminSession() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", adminEmail, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();
    }

    private long createCategory(Cookie[] admin, String name, String authorLabel) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("slug", "");
        body.put("description", "");
        body.put("authorLabel", authorLabel);
        body.put("displayOrder", 5);
        body.put("active", true);

        MvcResult created = mockMvc.perform(post("/api/v1/admin/categories")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private long defineAttribute(Cookie[] admin, String label, Long categoryId, int order)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label", label);
        body.put("slug", "");
        body.put("categoryId", categoryId);
        body.put("helpText", "");
        body.put("displayOrder", order);
        body.put("active", true);

        MvcResult created = mockMvc.perform(post("/api/v1/admin/attributes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    /**
     * The whole point, as one test: a new kind of product, with fields nobody
     * wrote into the code, visible on the shop.
     */
    @Test
    @DisplayName("a shop can start selling books without a migration")
    void sellsASecondKindOfThing() throws Exception {
        Cookie[] admin = adminSession();

        long books = createCategory(admin, "Books", "Author");
        defineAttribute(admin, "ISBN", books, 1);
        defineAttribute(admin, "Publisher", books, 2);
        defineAttribute(admin, "Pages", books, 3);

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("sku", "BK-0001");
        product.put("name", "Midnights Children");
        product.put("slug", "");
        product.put("categoryId", books);
        product.put("price", "499.00");
        product.put("stockQuantity", 4);
        product.put("lowStockThreshold", 1);
        product.put("featured", false);
        product.put("status", "ACTIVE");
        product.put("placeholderContent", false);
        product.put("author", "Salman Rushdie");
        product.put("attributes", Map.of(
                "isbn", "978-0099578512",
                "publisher", "Vintage",
                "pages", "672"));

        MvcResult created = mockMvc.perform(post("/api/v1/admin/products")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(product)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("Salman Rushdie"))
                .andExpect(jsonPath("$.authorLabel").value("Author"))
                .andReturn();
        String slug = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("slug").asText();

        // the shop shows it, labelled as the category decided
        mockMvc.perform(get("/api/v1/products/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("Salman Rushdie"))
                .andExpect(jsonPath("$.authorLabel").value("Author"))
                .andExpect(jsonPath("$.attributes.length()").value(3))
                // in the order the administrator put them in
                .andExpect(jsonPath("$.attributes[0].label").value("ISBN"))
                .andExpect(jsonPath("$.attributes[0].value").value("978-0099578512"))
                .andExpect(jsonPath("$.attributes[2].label").value("Pages"));
    }

    /**
     * The reason author is a column and not one of the attributes: customers
     * look for a book by who wrote it at least as often as by its title.
     */
    @Test
    @DisplayName("a search finds a book by its author")
    void searchesByAuthor() throws Exception {
        Cookie[] admin = adminSession();
        long books = createCategory(admin, "Books", "Author");

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("sku", "BK-0002");
        product.put("name", "The God of Small Things");
        product.put("slug", "");
        product.put("categoryId", books);
        product.put("price", "450.00");
        product.put("stockQuantity", 2);
        product.put("lowStockThreshold", 1);
        product.put("featured", false);
        product.put("status", "ACTIVE");
        product.put("placeholderContent", false);
        product.put("author", "Arundhati Roy");

        mockMvc.perform(post("/api/v1/admin/products").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(product)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products").param("q", "arundhati"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("The God of Small Things"));
    }

    @Test
    @DisplayName("a product is only asked for its own category's fields")
    void doesNotAskForAnotherCategorysFields() throws Exception {
        Cookie[] admin = adminSession();

        long books = createCategory(admin, "Books", "Author");
        defineAttribute(admin, "ISBN", books, 1);

        // the seeded bird house belongs to another category entirely
        MvcResult birdHouse = mockMvc.perform(
                        get("/api/v1/admin/products/1").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();

        String body = birdHouse.getResponse().getContentAsString();
        assertThat(body).doesNotContain("ISBN");
        // and it keeps the craft fields the migration carried across
        assertThat(body).contains("Material");
    }

    @Test
    @DisplayName("an attribute with no category applies to everything")
    void globalAttributeAppliesEverywhere() throws Exception {
        Cookie[] admin = adminSession();
        defineAttribute(admin, "Country of origin", null, 9);

        mockMvc.perform(get("/api/v1/admin/products/1").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes[?(@.label=='Country of origin')]").exists());
    }

    @Test
    @DisplayName("an attribute products have answered cannot be deleted")
    void refusesToDeleteAnAnsweredAttribute() throws Exception {
        Cookie[] admin = adminSession();

        long material = attributes.findBySlug("material").orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/admin/attributes/" + material).cookie(admin).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://karvya.example/problems/attribute-in-use"));
    }

    @Test
    @DisplayName("only an administrator can define one")
    void definingRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/attributes")).andExpect(status().isUnauthorized());
    }
}
