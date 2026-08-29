package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /**
     * Clears the leading image so a new one can be set.
     *
     * <p>A partial unique index allows only one primary per product, so the old
     * flag has to be cleared and flushed before the new one is written.
     */
    @Modifying
    @Query("update ProductImage i set i.primary = false where i.product.id = :productId and i.primary = true")
    void clearPrimaryFor(Long productId);

    long countByProductId(Long productId);
}
