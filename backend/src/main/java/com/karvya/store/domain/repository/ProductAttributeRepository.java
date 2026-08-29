package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    List<ProductAttribute> findAllByOrderByDisplayOrderAscLabelAsc();

    Optional<ProductAttribute> findBySlug(String slug);

    /**
     * What a product in this category should be asked for: the category's own
     * definitions and the ones that apply to everything.
     */
    @Query("""
            select a from ProductAttribute a
             where a.active = true
               and (a.category is null or a.category.id = :categoryId)
             order by a.displayOrder asc, a.label asc
            """)
    List<ProductAttribute> findForCategory(Long categoryId);

    @Query("select count(v) from ProductAttributeValue v where v.attribute.id = :attributeId")
    long countValues(Long attributeId);
}
