package com.karvya.store.web;

import com.karvya.store.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The public catalogue contract.
 *
 * <p>These assert real outcomes - which products come back and in what order -
 * rather than merely that the endpoint responds.
 */
class CatalogApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("returns the seeded catalogue")
    void listsSeededProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].image.key").exists())
                .andExpect(jsonPath("$.content[0].image.alt").isNotEmpty());
    }

    @Test
    @DisplayName("featured filter returns exactly the three the brief names")
    void filtersFeatured() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("featured", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].sku",
                        org.hamcrest.Matchers.containsInAnyOrder("KV-BH-01", "KV-BH-02", "KV-BH-05")));
    }

    /**
     * The regression guard for the bug that broke every search: a null {@code q}
     * used to bind untyped and PostgreSQL rejected {@code lower(bytea)}.
     */
    @Test
    @DisplayName("search works with no filters at all")
    void searchWithoutFiltersDoesNotBindUntypedNulls() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "NEWEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    @DisplayName("text search is case-insensitive and covers the short description")
    void searchesNameAndDescription() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("q", "TEARDROP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("KV-BH-03"));

        // "copper" appears only in the short description of KV-BH-05
        mockMvc.perform(get("/api/v1/products").param("q", "copper"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("KV-BH-05"));
    }

    @Test
    @DisplayName("price sorts run in the right direction")
    void sortsByPrice() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("KV-BH-02"))
                .andExpect(jsonPath("$.content[4].sku").value("KV-BH-05"));

        mockMvc.perform(get("/api/v1/products").param("sort", "PRICE_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("KV-BH-05"))
                .andExpect(jsonPath("$.content[4].sku").value("KV-BH-02"));
    }

    @Test
    @DisplayName("a reversed price range is corrected rather than returning nothing")
    void correctsReversedPriceRange() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("minPrice", "1300").param("maxPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("page size is capped so a caller cannot ask for the whole table")
    void capsPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(48));
    }

    @Test
    @DisplayName("an unknown category yields an empty page, not an error")
    void unknownCategoryIsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("category", "no-such-category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("product detail carries attributes and the whole gallery")
    void returnsProductDetail() throws Exception {
        mockMvc.perform(get("/api/v1/products/twin-entrance-coir-bird-house"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("KV-BH-05"))
                .andExpect(jsonPath("$.images.length()").value(2))
                // Material is no longer a column but an attribute an
                // administrator defines, and the migration carried the seeded
                // values across rather than dropping them with the column.
                .andExpect(jsonPath("$.attributes[?(@.label=='Material')].value").isNotEmpty())
                // seeded copy is flagged so the storefront can mark it provisional
                .andExpect(jsonPath("$.placeholderContent").value(true));
    }

    @Test
    @DisplayName("related products exclude the product itself")
    void relatedExcludesSelf() throws Exception {
        mockMvc.perform(get("/api/v1/products/twin-entrance-coir-bird-house/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].sku",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("KV-BH-05"))));
    }

    @Test
    @DisplayName("an unknown slug is a problem document, not a stack trace")
    void unknownSlugReturnsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/products/no-such-product"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://karvya.example/problems/product-not-found"))
                .andExpect(jsonPath("$.detail").value("The requested resource does not exist."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("categories report how many products are actually visible")
    void listsCategoriesWithCounts() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("bird-houses-and-nests"))
                .andExpect(jsonPath("$[0].productCount").value(5));
    }
}
