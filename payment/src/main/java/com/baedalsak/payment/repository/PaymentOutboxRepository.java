package com.baedalsak.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baedalsak.payment.domain.PaymentOutbox;

public interface PaymentOutboxRepository extends JpaRepository<PaymentOutbox, Long>{

}
