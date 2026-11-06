package com.baedalsak.delivery.location;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

/**
 * 카카오 로컬 주소 → 좌표: x·y 순서, 쉼표 뒤 상세 주소 재시도, 실패 시 빈 값
 */
class KakaoLocalClientTest {

    private static final String FOUND = """
            {"documents":[{"address_name":"서울 성동구 성수동2가 320-1","x":"127.0565","y":"37.5410"}],"meta":{"total_count":1}}
            """;
    private static final String EMPTY = """
            {"documents":[],"meta":{"total_count":0}}
            """;

    private MockRestServiceServer server;
    private KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoLocalClient(properties("test-key"), builder);
    }

    @Test
    void readsXAsLongitudeAndYAsLatitude() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/address.json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("query", encoded("서울 성동구 성수이로 77")))
                .andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        assertThat(client.geocode("서울 성동구 성수이로 77")).contains(new GeoPoint(37.5410, 127.0565));
        server.verify();
    }

    @Test
    void retriesWithAddressBeforeCommaWhenNotFound() {
        server.expect(queryParam("query", encoded("서울 성동구 성수이로 77, 302호")))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));
        server.expect(queryParam("query", encoded("서울 성동구 성수이로 77")))
                .andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        assertThat(client.geocode("서울 성동구 성수이로 77, 302호")).contains(new GeoPoint(37.5410, 127.0565));
        server.verify();
    }

    @Test
    void returnsEmptyWithoutThrowingWhenKakaoFails() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.geocode("서울 성동구 성수이로 77")).isEmpty();
    }

    @Test
    void doesNotCallKakaoWithoutKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer noCall = MockRestServiceServer.bindTo(builder).build();
        KakaoLocalClient disabled = new KakaoLocalClient(properties(""), builder);

        assertThat(disabled.geocode("서울 성동구 성수이로 77")).isEmpty();
        noCall.verify();   // 기대한 요청 없음 = 호출 0번
    }

    // 요청 URL 의 쿼리는 인코딩된 채로 비교된다
    private static String encoded(String query) {
        return UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
    }

    private static KakaoProperties properties(String key) {
        return new KakaoProperties(key, "https://dapi.kakao.com", "https://apis-navi.kakaomobility.com",
                Duration.ofMillis(500), Duration.ofSeconds(2));
    }
}
