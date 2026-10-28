package com.baedalsak.delivery.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * 배달 상태. 배차 대기 → 배차 완료 → 픽업 완료 → 배달 완료 순서로만 바뀐다 (설계서 '상태 전이' 탭).
 */
public enum DeliveryStatus {
    WAITING,    // 배차 대기
    ASSIGNED,   // 배차 완료
    PICKED_UP,  // 픽업 완료
    DELIVERED;  // 배달 완료

    // 라이더의 진행 중 배달. 라이더는 이 상태의 배달을 1건만 가진다 (6.2.2 수락 제한, 6.4.2 진행 중 배달 조회)
    public static final Set<DeliveryStatus> ACTIVE = EnumSet.of(ASSIGNED, PICKED_UP);
}
