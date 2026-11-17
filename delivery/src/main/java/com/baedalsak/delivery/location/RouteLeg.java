package com.baedalsak.delivery.location;

import com.baedalsak.delivery.domain.DeliveryStatus;

/**
 * 라이더가 지금 가고 있는 구간. 배달 상태로 정해진다.
 */
public enum RouteLeg {
    /** 배차 완료(ASSIGNED): 라이더 현재 위치 → 가게 */
    TO_STORE,
    /** 픽업 후(PICKED_UP · DELIVERED): 가게 → 배달지 */
    TO_CUSTOMER;

    /**
     * @param status 배달 상태
     * @return 배차 완료면 TO_STORE, 그 밖에는 TO_CUSTOMER
     */
    public static RouteLeg of(DeliveryStatus status) {
        return status == DeliveryStatus.ASSIGNED ? TO_STORE : TO_CUSTOMER;
    }

    /**
     * @param leg 저장된 구간. 구간을 나누기 전에 저장된 값이면 null
     * @return null 이면 TO_CUSTOMER (그때는 픽업 후 구간만 있었음)
     */
    public static RouteLeg orDefault(RouteLeg leg) {
        return leg == null ? TO_CUSTOMER : leg;
    }
}
