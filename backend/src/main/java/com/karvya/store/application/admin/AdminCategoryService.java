package com.karvya.store.application.admin;

import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.Category;
import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.model.ProductStatus;
import com.karvya.store.domain.repository.CategoryRepository;
import com.karvya.store.domain.repository.ProductRepository;
import com.karvya.store.domain.repository.ProductSpecifications;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Category management. Like products, categories are deactivated rather than deleted. */
@Service
public class AdminCategoryService {

    public record CategoryView(
            Long id,
            String name,
            String slug,
            String description,
            /** What this category calls its indexed field, or null if it has none. */
            String authorLabel,
            int displayOrder,
            boolean active,
            long productCount
    ) {
    }

    public record Upsert(
            @NotBlank(message = "Enter a name")
            @Size(max = 160) String name,

            @Size(max = 160)
            @Pattern(regexp = "^$|^[a-z0-9-]{2,160}$",
                    message = "A slug may use lower-case letters, numbers and hyphens only")
            String slug,

            @Size(max = 2000) String description,

            /**
             * "Author" for a book, "Artist" for a record, empty for something
             * nobody wrote. Empty hides the field on the product form and on
             * the product page.
             */
            @Size(max = 40) String authorLabel,

            int displayOrder,

            @NotNull(message = "Choose whether the category is active")
            Boolean active
    ) {
    }

    private final CategoryRepository categories;
    private final ProductRepository products;

    public AdminCategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listAll() {
        return categories.findAll().stream()
                .sorted(java.util.Comparator
                        .comparingInt(Category::getDisplayOrder)
                        .thenComparing(Category::getName))
                .map(category -> new CategoryView(category.getId(), category.getName(), category.getSlug(), category.getDescription(), category.getAuthorLabel(), category.getDisplayOrder(), category.isActive(),
                        countProducts(category)))
                .toList();
    }

    @Transactional
    public CategoryView create(Upsert request, String actor) {
        String slug = resolveSlug(request.slug(), request.name(), null);

        Category category = Category.create(request.name().trim(), slug);
        apply(category, request, actor);
        categories.save(category);

        return toView(category);
    }

    @Transactional
    public CategoryView update(Long id, Upsert request, String actor) {
        Category category = require(id);
        category.setName(request.name().trim());
        category.setSlug(resolveSlug(request.slug(), request.name(), id));
        apply(category, request, actor);
        return toView(category);
    }

    /**
     * Deactivating hides a category from the storefront. Its products are left
     * alone deliberately: they may belong somewhere else next, and silently
     * withdrawing them would be a surprise.
     */
    @Transactional
    public CategoryView setActive(Long id, boolean active, String actor) {
        Category category = require(id);

        if (!active && countProducts(category) > 0) {
            // not a refusal, but the caller should have been warned; the count
            // comes back so the interface can say what is affected
            category.setActive(false);
            category.setUpdatedBy(actor);
            return toView(category);
        }

        category.setActive(active);
        category.setUpdatedBy(actor);
        return toView(category);
    }

    private void apply(Category category, Upsert request, String actor) {
        category.setDescription(
                (request.description() == null || request.description().isBlank())
                        ? null : request.description().trim());
        category.setAuthorLabel(
                request.authorLabel() == null || request.authorLabel().isBlank()
                        ? null : request.authorLabel().trim());
        category.setDisplayOrder(request.displayOrder());
        category.setActive(Boolean.TRUE.equals(request.active()));
        category.setUpdatedBy(actor);
    }

    private long countProducts(Category category) {
        return products.count(ProductSpecifications.any()
                .and(ProductSpecifications.inCategory(category.getSlug()))
                .and(ProductSpecifications.hasStatus(ProductStatus.ACTIVE)));
    }

    private CategoryView toView(Category category) {
        return new CategoryView(category.getId(), category.getName(), category.getSlug(), category.getDescription(), category.getAuthorLabel(), category.getDisplayOrder(), category.isActive(),
                countProducts(category));
    }

    private Category require(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", String.valueOf(id)));
    }

    /** Slugs appear in URLs, so an existing one is never silently reused. */
    private String resolveSlug(String requested, String name, Long excludeId) {
        String base = (requested != null && !requested.isBlank()) ? requested.trim() : slugify(name);
        String candidate = base;
        int suffix = 2;
        while (categories.findBySlug(candidate)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String text) {
        String normalised = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalised.isBlank() ? "category" : normalised;
    }
}
