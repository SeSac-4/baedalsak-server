package com.baedalsak.delivery.location;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오 길찾기: "경도,위도" 파라미터, vertexes 파싱(경도 먼저), 도로 경계 중복 제거, 실패 시 빈 값
 */
class KakaoDirectionsClientTest {

    private static final GeoPoint STORE = new GeoPoint(37.5446, 127.0559);
    private static final GeoPoint HOME = new GeoPoint(37.5410, 127.0565);

    // 도로 2개. 첫 도로 끝점(127.0562, 37.5430)이 둘째 도로 시작점과 같다
    private static final String FOUND = """
            {"trans_id":"t","routes":[{"result_code":0,"result_msg":"길찾기 성공",
              "summary":{"distance":512,"duration":95},
              "sections":[{"distance":512,"duration":95,"roads":[
                {"name":"성수이로","vertexes":[127.0559,37.5446,127.0562,37.5430]},
                {"name":"아차산로","vertexes":[127.0562,37.5430,127.0565,37.5410]}
              ]}]}]}
            """;
    private static final String TOO_CLOSE = """
            {"trans_id":"t","routes":[{"result_code":104,"result_msg":"출발지와 도착지가 5 m 이내로 설정된 경우 경로를 탐색할 수 없음"}]}
            """;

    private MockRestServiceServer server;
    private KakaoDirectionsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoDirectionsClient(properties("test-key"), builder);
    }

    @Test
    void sendsLongitudeFirstAndParsesRoute() {
        server.expect(requestTo(startsWith("https://apis-navi.kakaomobility.com/v1/directions")))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("origin", "127.0559,37.5446"))
                .andExpect(queryParam("destination", "127.0565,37.541"))
                .andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        Route route = client.findRoute(STORE, HOME).orElseThrow();

        assertThat(route.path()).containsExactly(STORE, new GeoPoint(37.5430, 127.0562), HOME);
        assertThat(route.distanceM()).isEqualTo(512);
        assertThat(route.durationS()).isEqualTo(95);
        assertThat(route.straight()).isFalse();
        server.verify();
    }

    @Test
    void returnsEmptyWhenNoRouteFound() {
        server.expect(requestTo(startsWith("https://apis-navi.kakaomobility.com")))
                .andRespond(withSuccess(TOO_CLOSE, MediaType.APPLICATION_JSON));

        assertThat(client.findRoute(STORE, HOME)).isEmpty();
    }

    @Test
    void returnsEmptyWithoutThrowingWhenKakaoFails() {
        server.expect(requestTo(startsWith("https://apis-navi.kakaomobility.com")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.findRoute(STORE, HOME)).isEmpty();
    }

    @Test
    void doesNotCallKakaoWithoutKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer noCall = MockRestServiceServer.bindTo(builder).build();

        assertThat(new KakaoDirectionsClient(properties(""), builder).findRoute(STORE, HOME)).isEmpty();
        noCall.verify();
    }

    @Test
    void appendVertexesIgnoresOddTrailingValue() {
        List<GeoPoint> path = new ArrayList<>();
        KakaoDirectionsClient.appendVertexes(path, List.of(127.0559, 37.5446, 127.0562));

        assertThat(path).containsExactly(STORE);
    }

    private static KakaoProperties properties(String key) {
        return new KakaoProperties(key, "https://dapi.kakao.com", "https://apis-navi.kakaomobility.com",
                Duration.ofMillis(500), Duration.ofSeconds(2));
    }
}
