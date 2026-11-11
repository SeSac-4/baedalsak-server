package com.baedalsak.member.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baedalsak.member.domain.SmsLog;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {

  Optional<SmsLog> findByEventId(UUID eventId);
}