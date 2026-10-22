package com.baedalsak.delivery.domain;

/**
 * 배달 상태. 배차 대기 → 배차 완료 → 픽업 완료 → 배달 완료 순서로만 바뀐다 (설계서 '상태 전이' 탭).
 */
public enum DeliveryStatus {
    WAITING,    // 배차 대기
    ASSIGNED,   // 배차 완료
    PICKED_UP,  // 픽업 완료
    DELIVERED   // 배달 완료
}
