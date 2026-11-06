package com.baedalsak.delivery.location;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * 카카오 로컬 API — 주소 → 좌표 (GET /v2/local/search/address.json).
 * 좌표가 없어도 배달은 진행돼야 하므로 키 없음 · 실패 · 결과 없음은 모두 예외 없이 빈 값으로 돌려준다.
 */
@Slf4j
@Component
public class KakaoLocalClient {

    private final KakaoProperties properties;
    private final RestClient restClient;

    @Autowired
    public KakaoLocalClient(KakaoProperties properties, ObservationRegistry observationRegistry) {
        this(properties, RestClient.builder()
                .requestFactory(requestFactory(properties))
                .observationRegistry(observationRegistry));
    }

    // 테스트에서 MockRestServiceServer 를 붙인 builder 를 넣는다
    KakaoLocalClient(KakaoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.localBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                .build();
    }

    /**
     * 주소를 좌표로 바꾼다. "성수이로 77, 302호" 처럼 쉼표 뒤 상세 주소 때문에 못 찾으면 쉼표 앞 주소로 한 번 더 찾는다.
     *
     * @param address 도로명 · 지번 주소 (상세 주소 포함 가능)
     * @return 첫 번째 검색 결과의 좌표. 키 없음 · 실패 · 결과 없음이면 빈 값
     */
    public Optional<GeoPoint> geocode(String address) {
        if (!properties.enabled() || address == null || address.isBlank()) {
            return Optional.empty();
        }
        Optional<GeoPoint> found = search(address.trim());
        int comma = address.indexOf(',');
        if (found.isEmpty() && comma > 0) {
            found = search(address.substring(0, comma).trim());
        }
        return found;
    }

    private Optional<GeoPoint> search(String query) {
        try {
            AddressSearchResponse response = restClient.get()
                    .uri(uri -> uri.path("/v2/local/search/address.json").queryParam("query", query).build())
                    .retrieve()
                    .body(AddressSearchResponse.class);
            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                return Optional.empty();
            }
            Document first = response.documents().getFirst();
            // 카카오 응답은 x = 경도, y = 위도 (문자열)
            return Optional.of(new GeoPoint(Double.parseDouble(first.y()), Double.parseDouble(first.x())));
        } catch (RestClientException | NumberFormatException | NullPointerException e) {
            log.warn("kakao geocode failed, coordinates left empty: cause={}", e.toString());
            return Optional.empty();
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(KakaoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    // 응답 중 쓰는 필드만. 나머지는 무시된다
    record AddressSearchResponse(List<Document> documents) {
    }

    record Document(String x, String y) {
    }
}
