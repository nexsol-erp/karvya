package com.karvya.store.application.catalog;

import com.karvya.store.application.catalog.dto.CategorySummary;
import com.karvya.store.application.catalog.dto.ProductDetail;
import com.karvya.store.application.catalog.dto.ProductSummary;
import com.karvya.store.application.common.PageResponse;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.model.ProductStatus;
import com.karvya.store.domain.repository.CategoryRepository;
import com.karvya.store.domain.repository.ProductRepository;
import com.karvya.store.domain.repository.ProductSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read side of the catalogue, serving the public storefront.
 *
 * <p>Every query here is pinned to {@link ProductStatus#ACTIVE}. Draft,
 * deactivated and archived products are invisible to this service by
 * construction rather than by a caller remembering to filter, which is what
 * keeps an unreleased product from leaking through a direct slug guess.
 */
@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    private static final int RELATED_PRODUCT_COUNT = 4;

    private final ProductRepository products;
    private final CategoryRepository categories;

    public CatalogQueryService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    public PageResponse<ProductSummary> search(ProductQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), query.sort().toSort());
        return PageResponse.from(products.findAll(toSpecification(query), pageable), ProductSummary::from);
    }

    /**
     * Builds the query from the filters that were actually supplied. Absent
     * filters contribute no predicate at all rather than a null-tolerant one,
     * which keeps untyped null parameters out of the generated SQL.
     */
    private Specification<Product> toSpecification(ProductQuery query) {
        Specification<Product> spec = ProductSpecifications.any()
                .and(ProductSpecifications.hasStatus(ProductStatus.ACTIVE));

        if (query.categorySlug() != null) {
            spec = spec.and(ProductSpecifications.inCategory(query.categorySlug()));
        }
        if (query.q() != null) {
            spec = spec.and(ProductSpecifications.matchesText(query.q()));
        }
        if (query.minPrice() != null) {
            spec = spec.and(ProductSpecifications.priceAtLeast(query.minPrice()));
        }
        if (query.maxPrice() != null) {
            spec = spec.and(ProductSpecifications.priceAtMost(query.maxPrice()));
        }
        if (query.featured() != null) {
            spec = spec.and(ProductSpecifications.isFeatured(query.featured()));
        }
        if (query.inStockOnly()) {
            spec = spec.and(ProductSpecifications.inStock());
        }
        return spec;
    }

    public ProductDetail findBySlug(String slug) {
        return products.findBySlugAndStatus(slug, ProductStatus.ACTIVE)
                .map(ProductDetail::from)
                .orElseThrow(() -> new NotFoundException("Product", slug));
    }

    public List<ProductSummary> findRelated(String slug) {
        Product product = products.findBySlugAndStatus(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Product", slug));

        return products.findRelated(
                        product.getCategory().getId(),
                        product.getId(),
                        ProductStatus.ACTIVE,
                        PageRequest.of(0, RELATED_PRODUCT_COUNT))
                .stream()
                .map(ProductSummary::from)
                .toList();
    }

    public List<CategorySummary> listCategories() {
        return categories.findActiveWithProductCounts();
    }
}
