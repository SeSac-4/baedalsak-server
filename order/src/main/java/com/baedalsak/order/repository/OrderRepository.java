package com.baedalsak.order.repository;

import com.baedalsak.order.domain.Order;
import com.baedalsak.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 내 주문 최신순 (orders_customer_idx: customer_id, created_at DESC)
    Page<Order> findAllByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);

    // 가게 주문 목록 (orders_store_status_idx: store_id, status, created_at DESC)
    Page<Order> findAllByStoreIdAndOwnerIdAndStatusInOrderByCreatedAtDescIdDesc(
            Long storeId, Long ownerId, Collection<OrderStatus> statuses, Pageable pageable);

    // 이 가게 주문이 다른 사장님 것이면 내 가게가 아니다 (조회 시 store 를 부르지 않고 owner_id 스냅샷으로 판단)
    boolean existsByStoreIdAndOwnerIdNot(Long storeId, Long ownerId);

    // 주문 수락: 결제완료(PAID) 이고 내 가게 주문일 때만 조리중으로 (상태 전이 탭 'status=PAID AND owner_id=요청자').
    // 조건부 UPDATE 라 동시에 두 번 눌러도 한 번만 성공한다. 0 이면 바뀐 행 없음
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Order o
               SET o.status = :cooking, o.estimatedReadyAt = :estimatedReadyAt, o.updatedAt = :now
             WHERE o.id = :orderId AND o.ownerId = :ownerId AND o.status = :paid
            """)
    int accept(@Param("orderId") Long orderId, @Param("ownerId") Long ownerId,
               @Param("estimatedReadyAt") OffsetDateTime estimatedReadyAt, @Param("now") OffsetDateTime now,
               @Param("paid") OrderStatus paid, @Param("cooking") OrderStatus cooking);

    // PaymentApproved: 결제대기이고 금액이 같을 때만 결제완료로 (상태 전이 탭 5행). 0 이면 바뀐 행 없음
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Order o
               SET o.status = :paid, o.updatedAt = :now
             WHERE o.id = :orderId AND o.status = :pendingPayment AND o.totalAmount = :amount
            """)
    int markPaid(@Param("orderId") Long orderId, @Param("amount") Integer amount, @Param("now") OffsetDateTime now,
                 @Param("pendingPayment") OrderStatus pendingPayment, @Param("paid") OrderStatus paid);

    // DeliveryPickedUp·DeliveryCompleted: 기대한 이전 상태일 때만 다음 상태로 (상태 전이 탭 7·8행)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Order o
               SET o.status = :to, o.updatedAt = :now
             WHERE o.id = :orderId AND o.status = :from
            """)
    int changeStatus(@Param("orderId") Long orderId, @Param("from") OrderStatus from, @Param("to") OrderStatus to,
                     @Param("now") OffsetDateTime now);

    // DeliveryAssigned: 배차된 라이더 이름 저장 (테이블 정의서 rider_name)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.riderName = :riderName, o.updatedAt = :now WHERE o.id = :orderId")
    int assignRider(@Param("orderId") Long orderId, @Param("riderName") String riderName,
                    @Param("now") OffsetDateTime now);
}
