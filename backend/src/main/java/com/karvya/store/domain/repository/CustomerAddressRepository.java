package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(Long userId);

    /**
     * Every read is scoped by owner in the query itself rather than fetched by
     * id and checked afterwards. That is what makes an insecure-direct-object
     * reference impossible here: there is no code path that loads someone
     * else's address in the first place.
     */
    Optional<CustomerAddress> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    /** Clears the existing default so the partial unique index cannot be violated. */
    @Modifying
    @Query("update CustomerAddress a set a.defaultAddress = false where a.user.id = :userId and a.defaultAddress = true")
    void clearDefaultFor(Long userId);

    long countByUserId(Long userId);
}
