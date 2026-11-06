package com.baedalsak.delivery.location;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 실시간 위치 설정 (application.yml delivery-gps).
 *
 * @param mode           위치 출처. virtual = 서버 가상 GPS, real = 라이더 앱 업로드
 * @param updateInterval 가상 GPS 갱신 주기
 * @param speedKmh       가상 GPS 이동 속도 (km/h)
 * @param arrivalRadiusM 도착으로 보는 배달지까지 거리 (m). 도착해도 자동 완료하지 않는다
 * @param locationTtl    Redis 위치 키 수명
 */
@ConfigurationProperties("delivery-gps")
public record DeliveryGpsProperties(
        Mode mode,
        Duration updateInterval,
        double speedKmh,
        double arrivalRadiusM,
        Duration locationTtl
) {

    /** 위치 출처 */
    public enum Mode {
        /** 서버가 길찾기 경로를 따라 좌표를 만든다 */
        VIRTUAL,
        /** 라이더 앱이 실제 GPS 좌표를 보낸다 */
        REAL
    }

    /**
     * @return 가상 GPS 를 돌려야 하면 true
     */
    public boolean isVirtual() {
        return mode == Mode.VIRTUAL;
    }
}
