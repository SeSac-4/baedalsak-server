package com.baedalsak.delivery.location;

/**
 * 경로 구간. 배차 완료(ASSIGNED)는 가게까지, 픽업 후는 배달지까지.
 */
public enum RouteLeg {
    /** 라이더 현재 위치 → 가게 */
    TO_STORE,
    /** 가게 → 배달지 */
    TO_CUSTOMER
}
