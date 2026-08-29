package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.model.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Catalogue filters as composable criteria.
 *
 * <p>These exist instead of one JPQL query with nullable parameters. That
 * approach fails on PostgreSQL: a parameter bound as null carries no type, the
 * driver sends it as {@code bytea}, and any expression needing a real type -
 * {@code lower(?)} in particular - dies with "function lower(bytea) does not
 * exist". Building predicates only for filters that are actually present means
 * no untyped null is ever bound, and the SQL stays as narrow as the request.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** Matches everything; the starting point for chaining. */
    public static Specification<Product> any() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Product> inCategory(String categorySlug) {
        return (root, query, cb) -> cb.equal(root.get("category").get("slug"), categorySlug);
    }

    /** Case-insensitive match against the product name or its short description. */
    public static Specification<Product> matchesText(String term) {
        return (root, query, cb) -> {
            String pattern = "%" + term.toLowerCase() + "%";
            Predicate onName = cb.like(cb.lower(root.get("name")), pattern);
            Predicate onSummary = cb.like(
                    cb.lower(cb.coalesce(root.get("shortDescription"), "")), pattern);
            return cb.or(onName, onSummary);
        };
    }

    public static Specification<Product> priceAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> isFeatured(boolean featured) {
        return (root, query, cb) -> cb.equal(root.get("featured"), featured);
    }

    public static Specification<Product> inStock() {
        return (root, query, cb) -> cb.greaterThan(root.get("stockQuantity"), 0);
    }
}
