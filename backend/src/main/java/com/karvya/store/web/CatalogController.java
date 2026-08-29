package com.karvya.store.web;

import com.karvya.store.application.catalog.CatalogQueryService;
import com.karvya.store.application.catalog.ProductQuery;
import com.karvya.store.application.catalog.ProductSort;
import com.karvya.store.application.catalog.dto.CategorySummary;
import com.karvya.store.application.catalog.dto.ProductDetail;
import com.karvya.store.application.catalog.dto.ProductSummary;
import com.karvya.store.application.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Public catalogue. No authentication; read-only. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalogue", description = "Public product and category browsing")
public class CatalogController {

    private final CatalogQueryService catalog;

    public CatalogController(CatalogQueryService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    @Operation(summary = "Search the catalogue with optional filters and sorting")
    public PageResponse<ProductSummary> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        return catalog.search(new ProductQuery(
                q, category, minPrice, maxPrice, featured, inStock,
                ProductSort.parse(sort), page, size));
    }

    @GetMapping("/products/{slug}")
    @Operation(summary = "Fetch one product by its slug")
    public ProductDetail getProduct(@PathVariable String slug) {
        return catalog.findBySlug(slug);
    }

    @GetMapping("/products/{slug}/related")
    @Operation(summary = "Up to four other products from the same category")
    public List<ProductSummary> getRelated(@PathVariable String slug) {
        return catalog.findRelated(slug);
    }

    @GetMapping("/categories")
    @Operation(summary = "Active categories with visible product counts")
    public List<CategorySummary> listCategories() {
        return catalog.listCategories();
    }
}
