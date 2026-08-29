package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Whether a product has ever been sold. Used to warn before archiving:
     * order history still points at the product row, which is why products are
     * archived rather than deleted.
     */
    boolean existsByProductId(Long productId);
}
