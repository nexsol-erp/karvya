package com.karvya.store.application.admin;

import com.karvya.store.application.admin.dto.AdminAttributeDtos;
import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.Category;
import com.karvya.store.domain.model.ProductAttribute;
import com.karvya.store.domain.repository.CategoryRepository;
import com.karvya.store.domain.repository.ProductAttributeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * The fields an administrator decided a product should have.
 *
 * <p>Scoped to a category, so a shop can sell more than one kind of thing
 * without asking a book for its care instructions. Adding a kind of product is
 * then a category and a handful of definitions - no migration, no deploy.
 */
@Service
public class AdminAttributeService {

    private static final Logger log = LoggerFactory.getLogger(AdminAttributeService.class);

    private final ProductAttributeRepository attributes;
    private final CategoryRepository categories;

    public AdminAttributeService(ProductAttributeRepository attributes,
                                 CategoryRepository categories) {
        this.attributes = attributes;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<AdminAttributeDtos.Row> list() {
        return attributes.findAllByOrderByDisplayOrderAscLabelAsc().stream()
                .map(a -> AdminAttributeDtos.Row.from(a, attributes.countValues(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminAttributeDtos.Detail find(Long id) {
        ProductAttribute attribute = require(id);
        return AdminAttributeDtos.Detail.from(attribute, attributes.countValues(id));
    }

    @Transactional
    public AdminAttributeDtos.Detail create(AdminAttributeDtos.Upsert request, String actor) {
        String slug = (request.slug() == null || request.slug().isBlank())
                ? slugify(request.label())
                : request.slug().trim();

        attributes.findBySlug(slug).ifPresent(existing -> {
            throw new ConflictException("attribute-slug-in-use",
                    "There is already an attribute using that name.");
        });

        ProductAttribute attribute = ProductAttribute.of(request.label(), slug);
        apply(attribute, request, actor);
        attributes.save(attribute);

        log.info("{} defined attribute {}", actor, slug);
        return AdminAttributeDtos.Detail.from(attribute, 0);
    }

    /**
     * Updates a definition. The slug is not among the things that can change:
     * it is what recorded values are keyed by, so editing it would orphan every
     * answer already given. Relabelling is free.
     */
    @Transactional
    public AdminAttributeDtos.Detail update(Long id, AdminAttributeDtos.Upsert request, String actor) {
        ProductAttribute attribute = require(id);
        attribute.setLabel(request.label());
        apply(attribute, request, actor);
        attributes.saveAndFlush(attribute);

        log.info("{} updated attribute {}", actor, attribute.getSlug());
        return AdminAttributeDtos.Detail.from(attribute, attributes.countValues(id));
    }

    /**
     * Removes a definition nothing has answered.
     *
     * <p>Refused once products have values against it: the cascade would delete
     * those answers, so the deletion would look like it worked while quietly
     * discarding what had been entered. Deactivating hides it from the form and
     * the page, and keeps what is already recorded.
     */
    @Transactional
    public void delete(Long id, String actor) {
        ProductAttribute attribute = require(id);
        long used = attributes.countValues(id);

        if (used > 0) {
            throw new ConflictException("attribute-in-use",
                    used + (used == 1 ? " product has" : " products have")
                            + " a value for " + attribute.getLabel()
                            + ". Deactivate it instead, which keeps what was entered.");
        }

        attributes.delete(attribute);
        log.info("{} removed attribute {}", actor, attribute.getSlug());
    }

    private void apply(ProductAttribute attribute, AdminAttributeDtos.Upsert request, String actor) {
        Category category = request.categoryId() == null ? null
                : categories.findById(request.categoryId())
                        .orElseThrow(() -> new NotFoundException(
                                "Category", String.valueOf(request.categoryId())));

        attribute.setCategory(category);
        attribute.setHelpText(blankToNull(request.helpText()));
        attribute.setDisplayOrder(request.displayOrder());
        attribute.setActive(request.active());
        attribute.setUpdatedBy(actor);
    }

    private ProductAttribute require(Long id) {
        return attributes.findById(id)
                .orElseThrow(() -> new NotFoundException("Attribute", String.valueOf(id)));
    }

    private String slugify(String text) {
        String normalised = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalised.isBlank() ? "attribute" : normalised;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
