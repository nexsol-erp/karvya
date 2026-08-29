package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Lookups always go through the normalised form so case cannot fork an account. */
    Optional<AppUser> findByEmailNormalized(String emailNormalized);

    boolean existsByEmailNormalized(String emailNormalized);

    boolean existsByPhone(String phone);

    @Query("select count(u) from AppUser u join u.roles r where r.code = :code")
    long countByRole(String code);

    @Query("""
            select distinct u from AppUser u
              join u.roles r
             where r.code = :roleCode
            """)
    Page<AppUser> findCustomers(String roleCode, Pageable pageable);

    /** One search box over name, email and phone. */
    @Query("""
            select distinct u from AppUser u
              join u.roles r
             where r.code = :roleCode
               and (lower(u.fullName) like lower(concat('%', :term, '%'))
                 or lower(u.emailNormalized) like lower(concat('%', :term, '%'))
                 or lower(coalesce(u.phone, '')) like lower(concat('%', :term, '%')))
            """)
    Page<AppUser> searchCustomers(String roleCode, String term, Pageable pageable);
}
