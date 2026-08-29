package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.model.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Catalogue search, driven by {@link ProductSpecifications}.
     *
     * <p>Overridden purely to attach the entity graph: the category is needed
     * for every card, and pulling it in the same statement avoids a query per
     * row. It is a single-valued association, so pagination still happens in
     * the database rather than in memory.
     */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "images"})
    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            where p.status = :status
              and p.category.id = :categoryId
              and p.id <> :excludeId
            order by p.featured desc, p.createdAt desc
            """)
    List<Product> findRelated(
            @Param("categoryId") Long categoryId,
            @Param("excludeId") Long excludeId,
            @Param("status") ProductStatus status,
            Pageable pageable);

    /**
     * Loads a set of products with their images, for pricing a cart. One query
     * rather than one per line, and the images come along because every cart
     * line shows a thumbnail.
     */
    @EntityGraph(attributePaths = {"category", "images"})
    List<Product> findByIdIn(Collection<Long> ids);

    /**
     * Locks the given products for update, in ascending id order.
     *
     * <p>The ordering is the whole point: two concurrent checkouts that touch
     * the same products acquire the locks in the same sequence, so one waits
     * for the other instead of the pair deadlocking.
     *
     * <p>The @Version column on Product would already stop an oversell on its
     * own, by failing whichever transaction lost the race. This lock exists so
     * that buyer queues rather than being turned away from stock that is
     * genuinely there - measured, without it three units sold only twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id asc")
    List<Product> lockAllByIdInOrder(@Param("ids") Collection<Long> ids);

    /**
     * Products at or below their own low-stock threshold. Compared per product
     * rather than against one global number, so a piece made in tens and one
     * made in twos can each be flagged at the level that suits it.
     */
    @Query("""
            select p from Product p
             where p.status = :status
               and p.stockQuantity <= p.lowStockThreshold
             order by p.stockQuantity asc, p.name asc
            """)
    List<Product> findLowStock(@Param("status") ProductStatus status);

    Optional<Product> findBySku(String sku);

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
