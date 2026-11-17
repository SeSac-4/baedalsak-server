package com.baedalsak.delivery.location;

import java.time.Instant;

/**
 * 가상 GPS 가 어느 구간을 언제부터 움직이고 있는지 (캐시 Redis delivery:sim:{id} 값).
 *
 * @param leg       구간
 * @param startedAt 그 구간 출발 시각 (가게까지 = 배차 시각, 배달지까지 = 픽업 시각)
 */
public record SimulationStart(RouteLeg leg, Instant startedAt) {

    private static final String SEPARATOR = ":";

    /**
     * @return Redis 저장 값 (예: TO_STORE:1762398000000)
     */
    String encode() {
        return leg.name() + SEPARATOR + startedAt.toEpochMilli();
    }

    /**
     * @param value Redis 저장 값. 구간을 나누기 전 값(시각만)은 배달지까지 구간으로 읽는다
     * @return 읽은 값
     */
    static SimulationStart decode(String value) {
        int at = value.indexOf(SEPARATOR);
        if (at < 0) {
            return new SimulationStart(RouteLeg.TO_CUSTOMER, Instant.ofEpochMilli(Long.parseLong(value)));
        }
        return new SimulationStart(RouteLeg.valueOf(value.substring(0, at)),
                Instant.ofEpochMilli(Long.parseLong(value.substring(at + 1))));
    }
}
