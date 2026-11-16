package com.baedalsak.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.baedalsak.delivery.location.GeoPoint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 배달 요청 (delivery 테이블). OrderAccepted 이벤트로 WAITING 상태로 생성된다 (6.1.1).
 * 가게·주소·고객 정보는 이벤트 페이로드 스냅샷이라 조회 시 다른 서비스를 호출하지 않는다.
 */
@Entity
@Table(name = "delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private Long orderId;

    @Column(nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, updatable = false)
    private Long storeId;

    @Column(nullable = false, length = 100)
    private String storeName;

    @Column(nullable = false)
    private String pickupAddress;

    @Column(nullable = false)
    private String deliveryAddress;

    @Column(nullable = false, length = 50)
    private String deliveryDong;

    @Column(nullable = false, length = 20)
    private String customerPhone;

    // 메일 수신자 (이벤트 customerEmail·ownerEmail). V2 이전에 생성된 행은 NULL
    private String customerEmail;

    private String ownerEmail;

    @Column(length = 200)
    private String riderRequest;

    @Column(nullable = false)
    private Integer deliveryFee;

    @Column(nullable = false)
    private OffsetDateTime estimatedReadyAt;

    // 좌표 (V5). 카카오 주소 변환 결과. 실패했거나 V5 이전 행은 NULL — 채우는 곳은 DeliveryGeocodeService 하나
    private Double pickupLat;

    private Double pickupLng;

    private Double deliveryLat;

    private Double deliveryLng;

    // 경로 (V6). 픽업 후 DeliveryRouteService 가 채운다. JSON [{lat, lng}, ...]
    @JdbcTypeCode(SqlTypes.JSON)
    private String routePath;

    // 끝의 대문자 한 글자는 기본 이름 규칙으로 route_distancem 이 되므로 컬럼 이름을 직접 적는다
    @Column(name = "route_distance_m")
    private Integer routeDistanceM;

    @Column(name = "route_duration_s")
    private Integer routeDurationS;

    private Long riderId;

    @Column(length = 50)
    private String riderName;

    // 라이더 운송수단 (V8). 배차 수락 때 채운다. null 이면 오토바이로 본다 (VehicleType.orDefault)
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime assignedAt;

    private OffsetDateTime pickedUpAt;

    private OffsetDateTime completedAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    private Delivery(Long orderId, Long customerId, Long ownerId, Long storeId, String storeName,
                     String pickupAddress, String deliveryAddress, String deliveryDong,
                     String customerPhone, String customerEmail, String ownerEmail,
                     String riderRequest, Integer deliveryFee, OffsetDateTime estimatedReadyAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.ownerId = ownerId;
        this.storeId = storeId;
        this.storeName = storeName;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.deliveryDong = deliveryDong;
        this.customerPhone = customerPhone;
        this.customerEmail = customerEmail;
        this.ownerEmail = ownerEmail;
        this.riderRequest = riderRequest;
        this.deliveryFee = deliveryFee;
        this.estimatedReadyAt = estimatedReadyAt;
        this.status = DeliveryStatus.WAITING;   // 생성된 요청은 배차 대기 (6.1.1)
    }

    /**
     * @return 픽업(가게) 좌표. 없으면 null
     */
    public GeoPoint getPickupPoint() {
        return GeoPoint.ofNullable(pickupLat, pickupLng);
    }

    /**
     * @return 배달지 좌표. 없으면 null
     */
    public GeoPoint getDeliveryPoint() {
        return GeoPoint.ofNullable(deliveryLat, deliveryLng);
    }

    /**
     * @return 픽업 · 배달지 좌표 중 하나라도 비어 있으면 true
     */
    public boolean isMissingCoordinates() {
        return getPickupPoint() == null || getDeliveryPoint() == null;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
