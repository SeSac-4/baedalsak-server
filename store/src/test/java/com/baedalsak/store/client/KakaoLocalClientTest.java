package com.baedalsak.store.client;

import com.baedalsak.store.domain.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오 로컬 API 주소 → 좌표 (#210). 응답은 카카오 주소 검색 문서의 documents[].x · y (문자열, x = 경도)
 */
class KakaoLocalClientTest {

    static final String BASE = "https://dapi.kakao.com";
    static final String PATH = BASE + "/v2/local/search/address.json";
    static final String FOUND = """
            {"documents":[{"address_name":"서울 성동구 성수동2가 277-17","x":"127.0558","y":"37.5445"}],"meta":{"total_count":1}}
            """;
    static final String EMPTY = """
            {"documents":[],"meta":{"total_count":0}}
            """;

    MockRestServiceServer server;
    KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        client = clientWithKey("test-key");
    }

    @Test
    void 주소를_찾으면_y_를_위도_x_를_경도로_준다() {
        expectQuery("서울 성동구 성수이로 77").andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        assertThat(client.geocode("서울 성동구 성수이로 77"))
                .isEqualTo(new GeocodeResult.Found(new GeoPoint(37.5445, 127.0558)));
        server.verify();
    }

    @Test
    void 결과가_없으면_NotFound() {
        expectQuery("없는주소 1").andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

        assertThat(client.geocode("없는주소 1")).isInstanceOf(GeocodeResult.NotFound.class);
    }

    @Test
    void 쉼표_뒤_상세_주소_때문에_못_찾으면_쉼표_앞_주소로_다시_찾는다() {
        expectQuery("서울 성동구 성수이로 77, 302호").andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));
        expectQuery("서울 성동구 성수이로 77").andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        assertThat(client.geocode("서울 성동구 성수이로 77, 302호")).isInstanceOf(GeocodeResult.Found.class);
        server.verify();
    }

    @Test
    void 오류_응답이면_Unavailable() {
        expectQuery("서울 성동구 성수이로 77").andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.geocode("서울 성동구 성수이로 77")).isInstanceOf(GeocodeResult.Unavailable.class);
    }

    @Test
    void 키가_틀려_401_이면_Unavailable() {
        expectQuery("서울 성동구 성수이로 77").andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.geocode("서울 성동구 성수이로 77")).isInstanceOf(GeocodeResult.Unavailable.class);
    }

    @Test
    void 응답하지_않으면_Unavailable() {
        expectQuery("서울 성동구 성수이로 77").andRespond(request -> {
            throw new ResourceAccessException("timeout", new IOException("timeout"));
        });

        assertThat(client.geocode("서울 성동구 성수이로 77")).isInstanceOf(GeocodeResult.Unavailable.class);
    }

    @Test
    void 키가_없으면_호출하지_않고_Unavailable() {
        client = clientWithKey("");
        server.expect(never(), anything());

        assertThat(client.geocode("서울 성동구 성수이로 77")).isInstanceOf(GeocodeResult.Unavailable.class);
        server.verify();
    }

    private KakaoLocalClient clientWithKey(String key) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        KakaoProperties properties = new KakaoProperties(key, BASE, Duration.ofMillis(500), Duration.ofSeconds(2));
        return new KakaoLocalClient(properties, builder.build());
    }

    private ResponseActions expectQuery(String query) {
        return server.expect(requestTo(startsWith(PATH))).andExpect(method(GET))
                .andExpect(queryParam("query", UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8)));
    }
}
