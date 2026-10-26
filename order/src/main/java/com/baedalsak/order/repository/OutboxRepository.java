package com.baedalsak.order.repository;

import com.baedalsak.order.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    // 미발행 행을 오래된 순으로 잠그며 가져온다. 트랜잭션 안에서만 호출 (docs 3-2 ①)
    // SKIP LOCKED: 다른 파드가 이미 잡은 행은 건너뜀 → 같은 행을 두 파드가 동시에 보내지 않음 (outbox_unpublished_idx)
    @Query(value = """
            SELECT * FROM outbox
             WHERE published_at IS NULL
             ORDER BY id
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findUnpublishedForUpdate(@Param("limit") int limit);
}
