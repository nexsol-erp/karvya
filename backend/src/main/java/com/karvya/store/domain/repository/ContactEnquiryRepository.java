package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.ContactEnquiry;
import com.karvya.store.domain.model.EnquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ContactEnquiryRepository extends JpaRepository<ContactEnquiry, Long> {

    Page<ContactEnquiry> findByStatusOrderByCreatedAtDesc(EnquiryStatus status, Pageable pageable);

    Page<ContactEnquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** One search box over name, email and subject - how someone actually looks. */
    @Query("""
            select e from ContactEnquiry e
             where lower(e.name) like lower(concat('%', :term, '%'))
                or lower(e.email) like lower(concat('%', :term, '%'))
                or lower(e.subject) like lower(concat('%', :term, '%'))
             order by e.createdAt desc
            """)
    Page<ContactEnquiry> search(String term, Pageable pageable);

    long countByStatus(EnquiryStatus status);

    List<ContactEnquiry> findTop5ByOrderByCreatedAtDesc();
}
