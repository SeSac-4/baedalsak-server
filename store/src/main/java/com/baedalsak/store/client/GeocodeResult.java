package com.baedalsak.store.client;

import com.baedalsak.store.domain.GeoPoint;

/**
 * 주소 → 좌표 변환 결과 (#210). 호출하는 쪽이 경우마다 다르게 처리한다.
 * - Found: 좌표를 찾음
 * - NotFound: 카카오가 응답했지만 그 주소가 없음 → 사장님에게 주소를 다시 입력받는다
 * - Unavailable: 키 없음 · 장애 · 시간 초과로 확인하지 못함 → 좌표 없이 저장한다
 */
public sealed interface GeocodeResult {

    record Found(GeoPoint location) implements GeocodeResult {
    }

    record NotFound() implements GeocodeResult {
    }

    record Unavailable() implements GeocodeResult {
    }
}
