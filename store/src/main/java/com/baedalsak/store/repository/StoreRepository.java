package com.baedalsak.store.repository;

import com.baedalsak.store.domain.Category;
import com.baedalsak.store.domain.Store;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);

    // 가게 목록 커서 조회 (#169): id 가 cursor 보다 큰 가게를 id 순으로 limit 개
    List<Store> findByIdGreaterThanOrderByIdAsc(Long cursor, Limit limit);

    List<Store> findByCategoryAndIdGreaterThanOrderByIdAsc(Category category, Long cursor, Limit limit);

    // 가게 목록 통계 (#169): 카테고리별 [category, 가게 수, 최저 배달비]
    @Query("SELECT s.category, COUNT(s), MIN(s.deliveryFee) FROM Store s GROUP BY s.category")
    List<Object[]> countAndMinDeliveryFeeByCategory();

    // 지금 영업 중인 가게 수. Store.isOpenAt 과 같은 조건: 운영 중 + 오늘 휴무 아님 + 오픈 이상 · 마감 미만
    @Query("""
            SELECT COUNT(s) FROM Store s JOIN BusinessHour h ON h.storeId = s.id
            WHERE s.operationStatus = com.baedalsak.store.domain.OperationStatus.OPEN
              AND h.dayOfWeek = :today AND h.closed = false
              AND h.openTime <= :now AND h.closeTime > :now
            """)
    long countOpenAt(Short today, LocalTime now);
}
