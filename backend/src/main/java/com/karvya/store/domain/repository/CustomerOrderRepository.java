package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.CustomerOrder;
import com.karvya.store.domain.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CustomerOrderRepository
        extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {

    Optional<CustomerOrder> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findWithDetailByOrderNumber(String orderNumber);

    /**
     * Loads an order for modification under a write lock.
     *
     * <p>Used by every status change. Cancelling reads {@code stockRestoredAt}
     * and then sets it; without the lock two concurrent cancellations could
     * both see it null and each return the stock, crediting it twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> lockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.orderNumber = :orderNumber")
    Optional<CustomerOrder> lockByOrderNumber(String orderNumber);

    /** A customer's own orders, newest first. Always filtered by owner. */
    @EntityGraph(attributePaths = "items")
    Page<CustomerOrder> findByUserIdOrderByPlacedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByOrderNumberAndUserId(String orderNumber, Long userId);

    long countByStatus(OrderStatus status);

    long countByPaymentStatus(com.karvya.store.domain.model.PaymentStatus status);

    /**
     * Order value over a window, excluding cancellations.
     *
     * <p>coalesce so an empty window returns zero rather than null - a
     * dashboard showing a blank where a figure belongs reads as broken.
     */
    @Query("""
            select coalesce(sum(o.total), 0)
              from CustomerOrder o
             where o.placedAt >= :since
               and o.status <> :excluded
            """)
    java.math.BigDecimal totalValueSince(java.time.Instant since, OrderStatus excluded);

    @Query("""
            select count(o)
              from CustomerOrder o
             where o.placedAt >= :since
               and o.status <> :excluded
            """)
    long countPlacedSince(java.time.Instant since, OrderStatus excluded);
}
