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

    public AdminProductService(ProductRepository products, CategoryRepository categories,
                               ProductImageRepository images, OrderItemRepository orderItems,
                               StorageService storage, ImageUploadValidator validator,
                               ImageRenditionService renditions) {
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.orderItems = orderItems;
        this.storage = storage;
        this.validator = validator;
        this.renditions = renditions;
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
        return AdminProductDtos.Detail.from(require(id));
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

        return AdminProductDtos.Detail.from(products.save(product));
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
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("product-modified",
                    "Somebody else saved this product while you were editing. Reload and try again.");
        }
        return AdminProductDtos.Detail.from(product);
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
        return AdminProductDtos.Detail.from(product);
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
        return AdminProductDtos.Detail.from(product);
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
        return AdminProductDtos.Detail.from(product);
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

        return AdminProductDtos.Detail.from(product);
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
        product.setMaterial(blankToNull(request.material()));
        product.setColour(blankToNull(request.colour()));
        product.setDimensions(blankToNull(request.dimensions()));
        product.setCareInstructions(blankToNull(request.careInstructions()));
        product.setStockQuantity(request.stockQuantity());
        product.setLowStockThreshold(request.lowStockThreshold());
        product.setFeatured(request.featured());
        product.setStatus(request.status());
        product.setPlaceholderContent(request.placeholderContent());
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

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
