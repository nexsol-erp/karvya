package com.karvya.store.application.admin;

import com.karvya.store.application.admin.dto.AdminProductDtos;
import com.karvya.store.application.common.PageResponse;
import com.karvya.store.application.media.ImageRenditionService;
import com.karvya.store.application.media.ImageRenditions;
import com.karvya.store.application.media.ImageUploadValidator;
import com.karvya.store.application.media.StorageService;
import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.*;
import com.karvya.store.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Catalogue management.
 *
 * <p>Products are archived, never deleted. An order's line items keep their own
 * snapshot of what was sold, but the product row is still referenced, and
 * removing it would leave a hole where a customer expects to see what they
 * bought.
 */
@Service
public class AdminProductService {

    private static final Logger log = LoggerFactory.getLogger(AdminProductService.class);
    private static final int MAX_IMAGES_PER_PRODUCT = 8;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final OrderItemRepository orderItems;
    private final StorageService storage;
    private final ImageUploadValidator validator;
    private final ImageRenditionService renditions;
    private final VendorRepository vendors;
    private final ProductAttributeRepository attributes;
    private final ProductAttributeValueRepository attributeValues;

    public AdminProductService(ProductRepository products, CategoryRepository categories,
                               ProductImageRepository images, OrderItemRepository orderItems,
                               StorageService storage, ImageUploadValidator validator,
                               ImageRenditionService renditions, VendorRepository vendors,
                               ProductAttributeRepository attributes,
                               ProductAttributeValueRepository attributeValues) {
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.orderItems = orderItems;
        this.storage = storage;
        this.validator = validator;
        this.renditions = renditions;
        this.vendors = vendors;
        this.attributes = attributes;
        this.attributeValues = attributeValues;
    }

    // ---- reading ----------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<AdminProductDtos.Row> list(String q, ProductStatus status, int page, int size) {
        Specification<Product> spec = ProductSpecifications.any();
        if (status != null) {
            spec = spec.and(ProductSpecifications.hasStatus(status));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(ProductSpecifications.matchesText(q.trim()));
        }

        var pageable = PageRequest.of(Math.max(0, page), size <= 0 ? 20 : Math.min(size, 100),
                Sort.by(Sort.Order.asc("sku")));

        return PageResponse.from(products.findAll(spec, pageable), AdminProductDtos.Row::from);
    }

    @Transactional(readOnly = true)
    public AdminProductDtos.Detail find(Long id) {
        Product product = require(id);
        return AdminProductDtos.Detail.from(product, attributesFor(product));
    }

    // ---- writing ----------------------------------------------------------

    @Transactional
    public AdminProductDtos.Detail create(AdminProductDtos.Upsert request, String actor) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        products.findBySku(sku).ifPresent(existing -> {
            throw new ConflictException("sku-in-use", "That SKU is already used by another product.");
        });

        String slug = resolveSlug(request.slug(), request.name(), null);

        Product product = Product.createDraft(sku, slug, request.name(), requireCategory(request.categoryId()));
        apply(product, request, actor);

        // saved first: an attribute value references the product, so it needs
        // an id before anything can be recorded against it
        Product saved = products.saveAndFlush(product);
        applyAttributes(saved, request.attributes());

        return AdminProductDtos.Detail.from(saved, attributesFor(saved));
    }

    /**
     * Updates a product, refusing a stale edit.
     *
     * <p>The version travels with the form. If it no longer matches, someone
     * else saved in between and this request would silently overwrite their
     * work, so it is rejected with a message that says as much.
     */
    @Transactional
    public AdminProductDtos.Detail update(Long id, AdminProductDtos.Upsert request, String actor) {
        Product product = require(id);

        if (request.version() != null && request.version() != product.getVersion()) {
            throw new ConflictException("product-modified",
                    "Somebody else saved this product while you were editing. "
                            + "Reload to see their changes before saving yours.");
        }

        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        products.findBySku(sku)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("sku-in-use",
                            "That SKU is already used by another product.");
                });

        product.setSku(sku);
        product.setSlug(resolveSlug(request.slug(), request.name(), id));
        product.setCategory(requireCategory(request.categoryId()));
        apply(product, request, actor);

        try {
            products.saveAndFlush(product);
            applyAttributes(product, request.attributes());
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("product-modified",
                    "Somebody else saved this product while you were editing. Reload and try again.");
        }
        return AdminProductDtos.Detail.from(product, attributesFor(product));
    }

    /**
     * Changes status. Archiving is how a product leaves the catalogue, and the
     * reason there is no delete: order history still points here.
     */
    @Transactional
    public AdminProductDtos.Detail changeStatus(Long id, ProductStatus status, String actor) {
        Product product = require(id);

        if (status == ProductStatus.ARCHIVED && product.isFeatured()) {
            // a featured product vanishing from the home page without warning
            // reads as a bug, so make it a deliberate two-step
            throw new ConflictException("featured-product-archived",
                    "Remove this product from the featured list before archiving it.");
        }

        product.setStatus(status);
        product.setUpdatedBy(actor);
        return AdminProductDtos.Detail.from(product, attributesFor(product));
    }

    /** Reports whether a product has ever been ordered, for the interface to warn on. */
    @Transactional(readOnly = true)
    public boolean hasBeenOrdered(Long productId) {
        return orderItems.existsByProductId(productId);
    }

    // ---- images -----------------------------------------------------------

    @Transactional
    public AdminProductDtos.Detail addImage(Long productId, MultipartFile file,
                                            String altText, String actor) {
        Product product = require(productId);

        if (product.getImages().size() >= MAX_IMAGES_PER_PRODUCT) {
            throw new ConflictException("too-many-images",
                    "A product can have at most " + MAX_IMAGES_PER_PRODUCT + " photographs.");
        }

        // validated before a single byte reaches the disk
        ImageUploadValidator.ValidatedImage validated = validator.validate(file);

        // one file per width, not the original: the storefront asks for
        // /media/{key}-{width}.jpg, so a single stored file would be requested
        // at a URL that was never written
        ImageRenditionService.Stored stored = renditions.store(
                "products/" + product.getSku().toLowerCase(Locale.ROOT), validated.bytes());

        ProductImage image = ProductImage.of(product, stored.baseKey(),
                (altText == null || altText.isBlank()) ? product.getName() : altText.trim(),
                validated.contentType(), validated.width(), validated.height(),
                (long) validated.bytes().length, stored.formats());

        // the first photograph leads the gallery unless told otherwise
        image.setDisplayOrder(product.getImages().size());
        image.setPrimary(product.getImages().isEmpty());

        product.addImage(image);
        product.setUpdatedBy(actor);
        products.saveAndFlush(product);

        log.info("Added image {} to product {}", stored.baseKey(), product.getSku());
        return AdminProductDtos.Detail.from(product, attributesFor(product));
    }

    /**
     * Reorders the gallery and sets which image leads it.
     *
     * <p>A partial unique index enforces one primary per product, so the old
     * one is cleared and flushed before the new one is set - otherwise both
     * rows collide inside the same statement batch.
     */
    @Transactional
    public AdminProductDtos.Detail reorderImages(Long productId, List<Long> imageIds,
                                                 Long primaryImageId, String actor) {
        Product product = require(productId);

        List<Long> owned = product.getImages().stream().map(ProductImage::getId).toList();
        if (!owned.containsAll(imageIds) || imageIds.size() != owned.size()) {
            throw new ConflictException("image-order-mismatch",
                    "The list of images does not match this product.");
        }

        images.clearPrimaryFor(productId);
        images.flush();

        Long leading = primaryImageId != null ? primaryImageId : imageIds.get(0);
        for (int position = 0; position < imageIds.size(); position++) {
            Long imageId = imageIds.get(position);
            ProductImage image = product.getImages().stream()
                    .filter(candidate -> candidate.getId().equals(imageId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Image", String.valueOf(imageId)));
            image.setDisplayOrder(position);
            image.setPrimary(imageId.equals(leading));
        }

        product.setUpdatedBy(actor);
        products.saveAndFlush(product);
        return AdminProductDtos.Detail.from(product, attributesFor(product));
    }

    @Transactional
    public AdminProductDtos.Detail deleteImage(Long productId, Long imageId, String actor) {
        Product product = require(productId);

        ProductImage image = product.getImages().stream()
                .filter(candidate -> candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Image", String.valueOf(imageId)));

        boolean wasPrimary = image.isPrimary();
        String key = image.getStorageKey();
        String formats = image.getFormats();

        product.getImages().remove(image);
        products.saveAndFlush(product);

        // never leave a product with photographs but no leading one
        if (wasPrimary && !product.getImages().isEmpty()) {
            product.getImages().get(0).setPrimary(true);
            products.saveAndFlush(product);
        }

        // the files go only after the row is safely gone, and every rendition
        // goes - one row is backed by a width per format, not a single file
        ImageRenditions.allKeys(key, formats).forEach(storage::delete);
        product.setUpdatedBy(actor);

        return AdminProductDtos.Detail.from(product, attributesFor(product));
    }

    // ---- plumbing ---------------------------------------------------------

    private Product require(Long id) {
        return products.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", String.valueOf(id)));
    }

    private Category requireCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", String.valueOf(id)));
    }

    private void apply(Product product, AdminProductDtos.Upsert request, String actor) {
        product.setName(request.name().trim());
        product.setShortDescription(blankToNull(request.shortDescription()));
        product.setDescription(blankToNull(request.description()));
        product.setPrice(request.price());
        product.setAuthor(blankToNull(request.author()));
        product.setStockQuantity(request.stockQuantity());
        product.setLowStockThreshold(request.lowStockThreshold());
        product.setFeatured(request.featured());
        product.setStatus(request.status());
        product.setPlaceholderContent(request.placeholderContent());

        // Cleared rather than left alone when no supplier is chosen: "made by
        // us" has to be expressible, and silently keeping the previous one
        // would make it impossible to undo an assignment.
        product.setVendor(request.vendorId() == null ? null
                : vendors.findById(request.vendorId())
                        .orElseThrow(() -> new NotFoundException(
                                "Vendor", String.valueOf(request.vendorId()))));
        product.setVendorPrice(request.vendorPrice());
        product.setVendorDeliveryTime(blankToNull(request.vendorDeliveryTime()));
        product.setUpdatedBy(actor);
    }

    /**
     * Uses the supplied slug, or derives one from the name, and guarantees it
     * is unique. Slugs are in URLs customers may have bookmarked, so an
     * existing one is never silently changed by a rename elsewhere.
     */
    private String resolveSlug(String requested, String name, Long excludeId) {
        String base = (requested != null && !requested.isBlank())
                ? requested.trim()
                : slugify(name);

        String candidate = base;
        int suffix = 2;
        while (slugTaken(candidate, excludeId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean slugTaken(String slug, Long excludeId) {
        return products.findBySlug(slug)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .isPresent();
    }

    private String slugify(String text) {
        String normalised = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalised.isBlank() ? "product" : normalised;
    }

    /**
     * What this product should be asked for, and what it currently says.
     *
     * <p>Driven by the definitions for its category rather than by the values
     * already recorded, so a newly added attribute appears on the form
     * immediately, with an empty answer, instead of only on products that
     * happen to have one.
     */
    private List<AdminProductDtos.AttributeValue> attributesFor(Product product) {
        Map<Long, String> recorded = attributeValues.findForProduct(product.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        v -> v.getAttribute().getId(), ProductAttributeValue::getValue));

        return attributes.findForCategory(product.getCategory().getId()).stream()
                .map(a -> new AdminProductDtos.AttributeValue(
                        a.getId(), a.getSlug(), a.getLabel(), a.getHelpText(),
                        recorded.get(a.getId())))
                .toList();
    }

    /**
     * Replaces what this product says, for the attributes its category defines.
     *
     * <p>Only those: a value submitted for an attribute belonging to another
     * category is ignored rather than stored, so switching a product's category
     * cannot leave answers behind that nothing will ever show or edit again.
     */
    private void applyAttributes(Product product, Map<String, String> submitted) {
        if (submitted == null) {
            return;
        }

        List<ProductAttribute> applicable =
                attributes.findForCategory(product.getCategory().getId());
        Map<Long, ProductAttributeValue> existing =
                attributeValues.findForProduct(product.getId()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                v -> v.getAttribute().getId(), v -> v));

        for (ProductAttribute attribute : applicable) {
            if (!submitted.containsKey(attribute.getSlug())) {
                continue;
            }

            String value = blankToNull(submitted.get(attribute.getSlug()));
            ProductAttributeValue current = existing.get(attribute.getId());

            if (value == null) {
                // blank means "this product does not have one", which is a
                // removal rather than an empty string to render
                if (current != null) {
                    attributeValues.delete(current);
                }
            } else if (current == null) {
                attributeValues.save(ProductAttributeValue.of(product, attribute, value));
            } else {
                current.setValue(value);
                attributeValues.save(current);
            }
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
