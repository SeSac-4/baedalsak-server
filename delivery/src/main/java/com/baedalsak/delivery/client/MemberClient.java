package com.baedalsak.delivery.client;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.baedalsak.delivery.domain.VehicleType;

import java.time.Duration;
import java.util.Optional;

/**
 * [내부] member 서비스 동기 호출 (설계서 '동기 호출' 탭). gateway 를 거치지 않고 K8s DNS 로 직접 호출한다.
 * 배차 수락(6.2.2) 시 라이더 이름 · 운송수단 스냅샷용. 실패해도 배차는 성공시켜야 하므로 예외를 던지지 않고 빈 값을 돌려준다.
 */
@Slf4j
@Component
public class MemberClient {

    // 배차 수락 목표 p95 300ms (N1.1) 안에서 끝나도록 짧게 잡는다
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(300);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);

    private final RestClient restClient;

    public MemberClient(@Value("${MEMBER_SERVICE_URL:http://localhost:8085}") String memberServiceUrl,
                        ObservationRegistry observationRegistry) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(memberServiceUrl)
                .requestFactory(requestFactory)
                .observationRegistry(observationRegistry)   // traceId 를 member 로 전파 (N5.2)
                .build();
    }

    /**
     * GET /internal/members/{memberId} 의 name · vehicleType. 404·타임아웃·연결 실패 등은 모두 빈 값.
     */
    public Optional<RiderInfo> findRider(Long memberId) {
        try {
            MemberInternal member = restClient.get()
                    .uri("/internal/members/{memberId}", memberId)
                    .retrieve()
                    .body(MemberInternal.class);
            return Optional.ofNullable(member)
                    .map(m -> new RiderInfo(m.name(), VehicleType.parse(m.vehicleType())));
        } catch (RestClientException e) {
            log.warn("member lookup failed, rider name · vehicle left empty: memberId={}, cause={}", memberId, e.toString());
            return Optional.empty();
        }
    }

    // 설계서 'API 명세' MemberInternal 중 delivery 가 쓰는 필드만. 나머지는 무시된다
    record MemberInternal(Long id, String name, String vehicleType) {
    }

    /**
     * @param name        라이더 이름
     * @param vehicleType 운송수단. 없거나 모르는 값이면 null
     */
    public record RiderInfo(String name, VehicleType vehicleType) {
    }
}
