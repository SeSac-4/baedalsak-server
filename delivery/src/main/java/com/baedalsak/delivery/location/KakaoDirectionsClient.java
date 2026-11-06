package com.baedalsak.delivery.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 카카오모빌리티 자동차 길찾기 (GET /v1/directions). 가게 → 배달지 도로 경로와 거리 · 소요 시간.
 * 키 없음 · 실패 · 경로 없음은 모두 예외 없이 빈 값 → 호출하는 쪽이 직선 경로로 대신한다.
 */
@Slf4j
@Component
public class KakaoDirectionsClient {

    private final KakaoProperties properties;
    private final RestClient restClient;

    @Autowired
    public KakaoDirectionsClient(KakaoProperties properties, ObservationRegistry observationRegistry) {
        this(properties, RestClient.builder()
                .requestFactory(requestFactory(properties))
                .observationRegistry(observationRegistry));
    }

    // 테스트에서 MockRestServiceServer 를 붙인 builder 를 넣는다
    KakaoDirectionsClient(KakaoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.mobilityBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                .build();
    }

    /**
     * 추천 경로 하나를 찾는다.
     *
     * @param from 출발 (가게)
     * @param to   도착 (배달지)
     * @return 도로 경로. 키 없음 · 실패 · 경로 없음이면 빈 값
     */
    public Optional<Route> findRoute(GeoPoint from, GeoPoint to) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        try {
            DirectionsResponse response = restClient.get()
                    .uri(uri -> uri.path("/v1/directions")
                            // 카카오 좌표 파라미터는 "경도,위도" 순서
                            .queryParam("origin", coordinate(from))
                            .queryParam("destination", coordinate(to))
                            .queryParam("priority", "RECOMMEND")
                            .build())
                    .retrieve()
                    .body(DirectionsResponse.class);
            return toRoute(response);
        } catch (RestClientException | IllegalArgumentException | NullPointerException e) {
            log.warn("kakao directions failed, fallback to straight line: cause={}", e.toString());
            return Optional.empty();
        }
    }

    private static Optional<Route> toRoute(DirectionsResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return Optional.empty();
        }
        RouteItem route = response.routes().getFirst();
        if (route.resultCode() != 0 || route.summary() == null || route.sections() == null) {
            // 104(출발지와 도착지가 너무 가까움) 등. 메시지만 남기고 직선으로 대신한다
            log.info("kakao directions no route: code={}, message={}", route.resultCode(), route.resultMsg());
            return Optional.empty();
        }
        List<GeoPoint> path = new ArrayList<>();
        for (Section section : route.sections()) {
            for (Road road : section.roads()) {
                appendVertexes(path, road.vertexes());
            }
        }
        if (path.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new Route(path, route.summary().distance(), route.summary().duration(), false));
    }

    // vertexes 는 [경도, 위도, 경도, 위도, ...] 1차원 배열. 도로가 바뀌는 지점은 앞 도로 끝점과 같아 중복을 뺀다
    static void appendVertexes(List<GeoPoint> path, List<Double> vertexes) {
        for (int i = 0; i + 1 < vertexes.size(); i += 2) {
            GeoPoint point = new GeoPoint(vertexes.get(i + 1), vertexes.get(i));
            if (path.isEmpty() || !path.getLast().equals(point)) {
                path.add(point);
            }
        }
    }

    private static String coordinate(GeoPoint point) {
        return point.lng() + "," + point.lat();
    }

    private static SimpleClientHttpRequestFactory requestFactory(KakaoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    // 응답 중 쓰는 필드만. 나머지는 무시된다 (카카오 응답은 snake_case)
    record DirectionsResponse(List<RouteItem> routes) {
    }

    record RouteItem(@JsonProperty("result_code") int resultCode,
                     @JsonProperty("result_msg") String resultMsg,
                     Summary summary,
                     List<Section> sections) {
    }

    record Summary(int distance, int duration) {
    }

    record Section(List<Road> roads) {
    }

    record Road(List<Double> vertexes) {
    }
}
