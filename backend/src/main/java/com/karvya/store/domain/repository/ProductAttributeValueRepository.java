package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductAttributeValueRepository
        extends JpaRepository<ProductAttributeValue, ProductAttributeValue.Key> {

    /** In the order the attributes are meant to be shown, not insertion order. */
    @Query("""
            select v from ProductAttributeValue v
              join fetch v.attribute a
             where v.product.id = :productId
               and a.active = true
             order by a.displayOrder asc, a.label asc
            """)
    List<ProductAttributeValue> findForProduct(Long productId);

    void deleteByProductId(Long productId);
}
