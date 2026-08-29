package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByActiveTrueOrderByDisplayOrderAscLabelAsc();

    Optional<PaymentMethod> findByCodeAndActiveTrue(String code);

    Optional<PaymentMethod> findByCode(String code);

    List<PaymentMethod> findAllByOrderByDisplayOrderAscLabelAsc();
}
