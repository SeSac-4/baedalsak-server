package com.baedalsak.store.client;

import com.baedalsak.store.domain.GeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * 카카오 로컬 API — 주소 → 좌표 (GET /v2/local/search/address.json, #210).
 * delivery 의 같은 이름 클라이언트와 달리 "주소 없음"과 "확인 못 함"을 구분해 돌려준다
 * (가게 등록은 주소가 없으면 다시 입력받고, 장애면 좌표 없이 저장해야 해서).
 */
@Slf4j
@Component
public class KakaoLocalClient {

    private final KakaoProperties properties;
    private final RestClient restClient;

    public KakaoLocalClient(KakaoProperties properties, @Qualifier("kakaoRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * 주소를 좌표로 바꾼다. "성수이로 77, 302호" 처럼 쉼표 뒤 상세 주소 때문에 못 찾으면 쉼표 앞 주소로 한 번 더 찾는다.
     *
     * @param address 도로명 · 지번 주소 (상세 주소 포함 가능)
     * @return 첫 번째 검색 결과의 좌표 / 주소 없음 / 키 없음 · 실패
     */
    public GeocodeResult geocode(String address) {
        if (!properties.enabled() || address == null || address.isBlank()) {
            return new GeocodeResult.Unavailable();
        }
        GeocodeResult result = search(address.strip());
        int comma = address.indexOf(',');
        if (result instanceof GeocodeResult.NotFound && comma > 0) {
            result = search(address.substring(0, comma).strip());
        }
        return result;
    }

    private GeocodeResult search(String query) {
        try {
            AddressSearchResponse response = restClient.get()
                    .uri(uri -> uri.path("/v2/local/search/address.json").queryParam("query", query).build())
                    .retrieve()
                    .body(AddressSearchResponse.class);
            if (response == null || response.documents() == null) {
                throw new RestClientException("빈 응답");
            }
            if (response.documents().isEmpty()) {
                return new GeocodeResult.NotFound();
            }
            Document first = response.documents().getFirst();
            // 카카오 응답은 x = 경도, y = 위도 (문자열)
            return new GeocodeResult.Found(new GeoPoint(Double.parseDouble(first.y()), Double.parseDouble(first.x())));
        } catch (RestClientException | NumberFormatException | NullPointerException e) {
            log.warn("kakao geocode failed: cause={}", e.toString());
            return new GeocodeResult.Unavailable();
        }
    }

    // 응답 중 쓰는 필드만. 나머지는 무시된다
    record AddressSearchResponse(List<Document> documents) {
    }

    record Document(String x, String y) {
    }
}
