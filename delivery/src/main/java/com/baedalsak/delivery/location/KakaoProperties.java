package com.baedalsak.delivery.location;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 카카오 REST API 설정 (application.yml kakao). 키는 서버에만 두고 브라우저로 내보내지 않는다.
 *
 * @param restApiKey      REST API 키 (KAKAO_REST_API_KEY). 비어 있으면 호출하지 않는다
 * @param localBaseUrl    카카오 로컬 API (주소 → 좌표)
 * @param mobilityBaseUrl 카카오모빌리티 API (길찾기)
 * @param connectTimeout  연결 제한 시간
 * @param readTimeout     응답 제한 시간
 */
@ConfigurationProperties("kakao")
public record KakaoProperties(
        String restApiKey,
        String localBaseUrl,
        String mobilityBaseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    /**
     * @return 키가 있어 카카오를 호출할 수 있으면 true
     */
    public boolean enabled() {
        return restApiKey != null && !restApiKey.isBlank();
    }
}
