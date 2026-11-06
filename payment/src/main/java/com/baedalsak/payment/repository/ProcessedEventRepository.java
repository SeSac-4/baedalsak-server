package com.baedalsak.payment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baedalsak.payment.domain.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

  // 처음이면 1, 이미 있으면 0 (order 와 같은 방식). save() 로 PK 위반을 잡으면 트랜잭션이 rollback-only 가 되므로 ON CONFLICT
  @Modifying
  @Query(value = """
      INSERT INTO processed_event (event_id, event_type)
      VALUES (:eventId, :eventType)
      ON CONFLICT (event_id) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("eventId") UUID eventId, @Param("eventType") String eventType);
}
