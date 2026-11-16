package com.baedalsak.store.repository;

import com.baedalsak.store.domain.Category;
import com.baedalsak.store.domain.NearbyArea;
import com.baedalsak.store.domain.Store;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    // 반경 조건 (#210). 파라미터 area (NearbyArea) 를 쓴다
    //   1) 위도 · 경도 범위(bounding box)로 좁혀 store_location_idx 를 탄다
    //   2) Haversine 으로 정확히 거른다. 지구 반지름 6371000m · 1도 = 0.017453292519943295 rad (GeoPoint 와 같은 식)
    //   좌표가 없는(null) 가게는 비교가 참이 아니라 빠진다
    // 조회 뒤 Java 에서 거르면 size + 1 로 정하는 hasNext 가 틀어지므로 반드시 WHERE 에서 거른다
    String WITHIN_RADIUS = """
            s.latitude BETWEEN :#{#area.minLat()} AND :#{#area.maxLat()}
            AND s.longitude BETWEEN :#{#area.minLng()} AND :#{#area.maxLng()}
            AND 2 * 6371000 * asin(sqrt(
                  power(sin((s.latitude - :#{#area.lat()}) * 0.017453292519943295 / 2), 2)
                + cos(:#{#area.lat()} * 0.017453292519943295) * cos(s.latitude * 0.017453292519943295)
                  * power(sin((s.longitude - :#{#area.lng()}) * 0.017453292519943295 / 2), 2)
            )) <= :#{#area.radiusM()}
            """;

    // 지금 영업 중: Store.isOpenAt 과 같은 조건 (운영 중 + 오늘 휴무 아님 + 오픈 이상 · 마감 미만)
    String OPEN_AT = """
            s.operationStatus = com.baedalsak.store.domain.OperationStatus.OPEN
            AND h.dayOfWeek = :today AND h.closed = false
            AND h.openTime <= :now AND h.closeTime > :now
            """;

    Optional<Store> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);

    // 가게 목록 커서 조회 (#169): id 가 cursor 보다 큰 가게를 id 순으로 limit 개
    List<Store> findByIdGreaterThanOrderByIdAsc(Long cursor, Limit limit);

    List<Store> findByCategoryAndIdGreaterThanOrderByIdAsc(Category category, Long cursor, Limit limit);

    // 가게 이름 검색 (#178): 부분 일치 · 대소문자 무시. Containing 은 Spring Data 가 % · _ · \ 를 이스케이프한다
    List<Store> findByNameContainingIgnoreCaseAndIdGreaterThanOrderByIdAsc(String name, Long cursor, Limit limit);

    List<Store> findByCategoryAndNameContainingIgnoreCaseAndIdGreaterThanOrderByIdAsc(
            Category category, String name, Long cursor, Limit limit);

    // 반경 안 가게 커서 조회 (#210). 정렬 · 커서는 위치 없는 목록과 같은 id 순
    //   category 는 null 이면 조건 없음. namePattern 은 LIKE 패턴 (검색어 없으면 "%", StoreQueryService.toLikePattern)
    //   namePattern 을 null 로 넘기면 PostgreSQL 이 타입을 bytea 로 받아 lower() 가 실패하므로 null 을 쓰지 않는다
    @Query("SELECT s FROM Store s WHERE (:category IS NULL OR s.category = :category)"
            + " AND lower(s.name) LIKE lower(:namePattern) ESCAPE '!'"
            + " AND s.id > :cursor AND " + WITHIN_RADIUS + " ORDER BY s.id")
    List<Store> findNearby(Category category, String namePattern, Long cursor, NearbyArea area, Limit limit);

    // 가게 목록 통계 (#169): 카테고리별 [category, 가게 수, 최저 배달비]
    @Query("SELECT s.category, COUNT(s), MIN(s.deliveryFee) FROM Store s GROUP BY s.category")
    List<Object[]> countAndMinDeliveryFeeByCategory();

    // 반경 안 가게만 (#210). 목록과 같은 반경 조건
    @Query("SELECT s.category, COUNT(s), MIN(s.deliveryFee) FROM Store s WHERE " + WITHIN_RADIUS
            + " GROUP BY s.category")
    List<Object[]> countAndMinDeliveryFeeByCategoryNearby(NearbyArea area);

    // 지금 영업 중인 가게 수
    @Query("SELECT COUNT(s) FROM Store s JOIN BusinessHour h ON h.storeId = s.id WHERE " + OPEN_AT)
    long countOpenAt(Short today, LocalTime now);

    // 반경 안에서 지금 영업 중인 가게 수 (#210)
    @Query("SELECT COUNT(s) FROM Store s JOIN BusinessHour h ON h.storeId = s.id WHERE " + OPEN_AT
            + " AND " + WITHIN_RADIUS)
    long countOpenAtNearby(Short today, LocalTime now, NearbyArea area);

    // 좌표 채우기 대상 (#210): 좌표가 없는 운영 중 가게
    List<Store> findByLatitudeIsNullOrderByIdAsc();

    // 좌표 채우기 (#210). 그 사이 사장님이 주소를 바꿨거나 좌표가 이미 채워졌으면 바꾸지 않는다 (0 반환)
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Store s SET s.latitude = :lat, s.longitude = :lng"
            + " WHERE s.id = :id AND s.address = :address AND s.latitude IS NULL")
    int fillLocation(Long id, String address, double lat, double lng);
}
