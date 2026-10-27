package com.baedalsak.delivery.repository;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * delivery 테이블.
 */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    boolean existsByOrderId(Long orderId);

    // 배차 대기 목록 (6.2.1, 오래된 순). delivery_waiting_idx (created_at) WHERE status='WAITING' 사용
    List<Delivery> findAllByStatusOrderByCreatedAtAscIdAsc(DeliveryStatus status);

    // 라이더의 진행 중 배달 여부 (6.2.2 동시 1건만). delivery_rider_active_idx (rider_id) WHERE status IN (...) 사용
    boolean existsByRiderIdAndStatusIn(Long riderId, Collection<DeliveryStatus> statuses);

    /**
     * 배차 수락 (6.2.2, N2.5). WAITING 일 때만 ASSIGNED 로 바꾸는 조건부 UPDATE. 바뀐 행 수(0 또는 1)를 돌려준다.
     * 여러 라이더가 동시에 수락하면 PostgreSQL 행 잠금으로 직렬화되고, 뒤에 온 UPDATE 는 앞 트랜잭션 커밋 후
     * WHERE 를 다시 평가해 0행이 된다 → 분산 락 없이 1명만 성공.
     * clearAutomatically: 벌크 UPDATE 는 영속성 컨텍스트를 거치지 않으므로, 이후 findById 가 새 값을 읽도록 비운다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Delivery d
               SET d.status = com.baedalsak.delivery.domain.DeliveryStatus.ASSIGNED,
                   d.riderId = :riderId,
                   d.riderName = :riderName,
                   d.assignedAt = :assignedAt,
                   d.updatedAt = :assignedAt
             WHERE d.id = :deliveryId
               AND d.status = com.baedalsak.delivery.domain.DeliveryStatus.WAITING
            """)
    int assignIfWaiting(@Param("deliveryId") Long deliveryId,
                        @Param("riderId") Long riderId,
                        @Param("riderName") String riderName,
                        @Param("assignedAt") OffsetDateTime assignedAt);

    /**
     * 픽업 완료 (6.3.1). 배정된 라이더 본인이고 ASSIGNED 일 때만 PICKED_UP 으로 바꾸는 조건부 UPDATE (설계서 '상태 전이' 탭).
     * 같은 요청이 두 번 와도 두 번째는 WHERE 에서 걸려 0행 → 이벤트가 중복 발행되지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Delivery d
               SET d.status = com.baedalsak.delivery.domain.DeliveryStatus.PICKED_UP,
                   d.pickedUpAt = :pickedUpAt,
                   d.updatedAt = :pickedUpAt
             WHERE d.id = :deliveryId
               AND d.riderId = :riderId
               AND d.status = com.baedalsak.delivery.domain.DeliveryStatus.ASSIGNED
            """)
    int pickUpIfAssigned(@Param("deliveryId") Long deliveryId,
                         @Param("riderId") Long riderId,
                         @Param("pickedUpAt") OffsetDateTime pickedUpAt);
}
