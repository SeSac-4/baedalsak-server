package com.baedalsak.delivery.repository;

import com.baedalsak.delivery.domain.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * outbox 테이블 (이벤트 발행, docs 3-2).
 */
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    /**
     * 미발행 행을 오래된 순으로 잠그며 가져온다. 트랜잭션 안에서만 호출한다.
     * SKIP LOCKED: 다른 파드(replicas 2)가 이미 잡은 행은 건너뜀 → 같은 행을 두 파드가 동시에 보내지 않음.
     * outbox_unpublished_idx (id) WHERE published_at IS NULL 사용
     */
    @Query(value = """
            SELECT * FROM outbox
             WHERE published_at IS NULL
             ORDER BY id
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Outbox> findUnpublishedForUpdate(@Param("limit") int limit);
}
