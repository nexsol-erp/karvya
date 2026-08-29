package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.OfflinePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfflinePaymentRepository extends JpaRepository<OfflinePayment, Long> {
    List<OfflinePayment> findByOrderIdOrderByReceivedOnAscIdAsc(Long orderId);
}
