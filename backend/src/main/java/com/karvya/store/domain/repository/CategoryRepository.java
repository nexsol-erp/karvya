package com.karvya.store.domain.repository;

import com.karvya.store.application.catalog.dto.CategorySummary;
import com.karvya.store.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Active categories with a count of the products actually visible in each.
     * The count is a correlated subquery rather than a join so that a category
     * with no live products still appears, showing zero.
     */
    @Query("""
            select new com.karvya.store.application.catalog.dto.CategorySummary(
                c.id, c.name, c.slug, c.description, c.imageKey,
                (select count(p) from Product p
                  where p.category = c
                    and p.status = com.karvya.store.domain.model.ProductStatus.ACTIVE))
            from Category c
            where c.active = true
            order by c.displayOrder asc, c.name asc
            """)
    List<CategorySummary> findActiveWithProductCounts();

    Optional<Category> findBySlugAndActiveTrue(String slug);

    Optional<Category> findBySlug(String slug);
}
