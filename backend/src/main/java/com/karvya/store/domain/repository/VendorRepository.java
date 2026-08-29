package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    List<Vendor> findAllByOrderByNameAsc();

    List<Vendor> findByActiveTrueOrderByNameAsc();

    /** How many products name this supplier, so the admin can warn before removing one. */
    @Query("select count(p) from Product p where p.vendor.id = :vendorId")
    long countProducts(Long vendorId);
}
