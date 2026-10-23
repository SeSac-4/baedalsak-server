package com.baedalsak.delivery.repository;

import com.baedalsak.delivery.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * processed_event 테이블 (소비 멱등, N2.4).
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * 처음 받은 이벤트면 1, 이미 처리한 이벤트면 0.
     * save() 로 PK 위반을 잡으면 트랜잭션이 rollback-only 가 되므로 ON CONFLICT 로 처리한다.
     * 같은 이벤트가 동시에 두 번 오면 뒤의 INSERT 는 앞 트랜잭션이 끝날 때까지 기다린 뒤 0을 반환한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_event (event_id, event_type)
            VALUES (:eventId, :eventType)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("eventType") String eventType);
}
