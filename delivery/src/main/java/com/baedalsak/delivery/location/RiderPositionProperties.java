package com.baedalsak.delivery.location;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가상 라이더 위치 설정 (application.yml rider-position). 처음 보는 라이더를 가게에서 얼마나 떨어진 곳에 둘지.
 *
 * @param startMinRadiusM 가게에서 최소 거리 (m)
 * @param startMaxRadiusM 가게에서 최대 거리 (m)
 */
@ConfigurationProperties("rider-position")
public record RiderPositionProperties(double startMinRadiusM, double startMaxRadiusM) {

    public RiderPositionProperties {
        if (startMinRadiusM < 0 || startMaxRadiusM < startMinRadiusM) {
            throw new IllegalArgumentException("rider-position radius must be 0 <= min <= max");
        }
    }
}
