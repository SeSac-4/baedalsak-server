package com.baedalsak.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baedalsak.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

}