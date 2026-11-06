package com.baedalsak.delivery.repository;

import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * delivery 테이블.
 */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    boolean existsByOrderId(Long orderId);

    // 주문의 배달 (6.4.1). order_id UNIQUE 인덱스 사용
    Optional<Delivery> findByOrderId(Long orderId);

    // 배차 대기 목록 (6.2.1, 오래된 순). delivery_waiting_idx (created_at) WHERE status='WAITING' 사용
    Page<Delivery> findAllByStatusOrderByCreatedAtAscIdAsc(DeliveryStatus status, Pageable pageable);

    // 라이더의 진행 중 배달 여부 (6.2.2 동시 1건만). delivery_rider_active_uq (rider_id) WHERE status IN (...) 사용
    boolean existsByRiderIdAndStatusIn(Long riderId, Collection<DeliveryStatus> statuses);

    // 라이더의 진행 중 배달 (6.4.2). 같은 delivery_rider_active_uq 사용. UNIQUE 라 0건 또는 1건
    Optional<Delivery> findFirstByRiderIdAndStatusInOrderByAssignedAtAscIdAsc(Long riderId,
                                                                              Collection<DeliveryStatus> statuses);

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

    /**
     * 배달 완료 (6.3.2). 배정된 라이더 본인이고 PICKED_UP 일 때만 DELIVERED 로 바꾸는 조건부 UPDATE (설계서 '상태 전이' 탭).
     * 같은 요청이 두 번 와도 두 번째는 WHERE 에서 걸려 0행 → 이벤트가 중복 발행되지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Delivery d
               SET d.status = com.baedalsak.delivery.domain.DeliveryStatus.DELIVERED,
                   d.completedAt = :completedAt,
                   d.updatedAt = :completedAt
             WHERE d.id = :deliveryId
               AND d.riderId = :riderId
               AND d.status = com.baedalsak.delivery.domain.DeliveryStatus.PICKED_UP
            """)
    int completeIfPickedUp(@Param("deliveryId") Long deliveryId,
                           @Param("riderId") Long riderId,
                           @Param("completedAt") OffsetDateTime completedAt);

    /**
     * 좌표 채우기 (배달실시간초안 5번). 비어 있는 좌표만 채우고, 이미 있는 값은 그대로 둔다 (COALESCE).
     * 상태 · 라이더 컬럼은 건드리지 않아, 배차 수락 등과 동시에 실행돼도 서로 덮어쓰지 않는다.
     * null 을 넘기면 그 좌표는 바뀌지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Delivery d
               SET d.pickupLat = COALESCE(d.pickupLat, :pickupLat),
                   d.pickupLng = COALESCE(d.pickupLng, :pickupLng),
                   d.deliveryLat = COALESCE(d.deliveryLat, :deliveryLat),
                   d.deliveryLng = COALESCE(d.deliveryLng, :deliveryLng)
             WHERE d.id = :deliveryId
            """)
    int fillCoordinates(@Param("deliveryId") Long deliveryId,
                        @Param("pickupLat") Double pickupLat,
                        @Param("pickupLng") Double pickupLng,
                        @Param("deliveryLat") Double deliveryLat,
                        @Param("deliveryLng") Double deliveryLng);

    /**
     * 경로 저장 (배달실시간초안 6번). 아직 경로가 없을 때만 저장해, 같은 픽업이 두 번 처리돼도 처음 값을 유지한다.
     * 경로 컬럼만 바꿔 상태 변경(완료 등)과 동시에 실행돼도 덮어쓰지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Delivery d
               SET d.routePath = :routePath,
                   d.routeDistanceM = :distanceM,
                   d.routeDurationS = :durationS
             WHERE d.id = :deliveryId
               AND d.routePath IS NULL
            """)
    int saveRouteIfAbsent(@Param("deliveryId") Long deliveryId,
                          @Param("routePath") String routePath,
                          @Param("distanceM") Integer distanceM,
                          @Param("durationS") Integer durationS);
}
