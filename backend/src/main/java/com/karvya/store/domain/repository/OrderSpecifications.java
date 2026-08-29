package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.CustomerOrder;
import com.karvya.store.domain.model.OrderStatus;
import com.karvya.store.domain.model.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Filters for the admin order list.
 *
 * <p>Criteria rather than one JPQL query with nullable parameters, for the
 * same reason as the catalogue: PostgreSQL cannot type a null bind parameter,
 * and building only the predicates that apply avoids the problem entirely.
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<CustomerOrder> any() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<CustomerOrder> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<CustomerOrder> hasPaymentStatus(PaymentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("paymentStatus"), status);
    }

    /**
     * One search box across order number, customer name, phone and email -
     * which is how someone actually looks for an order when a customer is on
     * the phone and offers whichever of those they can remember.
     */
    public static Specification<CustomerOrder> matches(String term) {
        return (root, query, cb) -> {
            String pattern = "%" + term.toLowerCase() + "%";
            Predicate onNumber = cb.like(cb.lower(root.get("orderNumber")), pattern);
            Predicate onName = cb.like(cb.lower(root.get("deliveryName")), pattern);
            Predicate onPhone = cb.like(cb.lower(root.get("deliveryPhone")), pattern);
            Predicate onEmail = cb.like(cb.lower(cb.coalesce(root.get("deliveryEmail"), "")), pattern);
            return cb.or(onNumber, onName, onPhone, onEmail);
        };
    }

    public static Specification<CustomerOrder> placedOnOrAfter(LocalDate date) {
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("placedAt"), from);
    }

    /** Inclusive of the whole day, which is what a person means by "to". */
    public static Specification<CustomerOrder> placedOnOrBefore(LocalDate date) {
        Instant until = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return (root, query, cb) -> cb.lessThan(root.get("placedAt"), until);
    }
}
