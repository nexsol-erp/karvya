package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * The whole cart in one query. Items and their products are always needed
     * together, since every line is re-priced from the catalogue on read.
     */
    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category"})
    Optional<Cart> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
