package com.baedalsak.store.service;

import com.baedalsak.store.client.GeocodeResult;
import com.baedalsak.store.domain.GeoPoint;
import com.baedalsak.store.repository.StoreRepository;
import com.baedalsak.store.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 좌표가 없는 가게 좌표 채우기 (#210). seed · 이전 가게 · 카카오 장애로 좌표 없이 저장된 가게
 */
class StoreLocationBackfillTest extends IntegrationTest {

    @Autowired
    StoreLocationBackfill backfill;

    @Autowired
    StoreRepository storeRepository;

    @Test
    void 좌표가_없는_가게만_채우고_못_찾은_가게는_건너뛴다() {
        insertStore(1L, "서울 성동구 성수이로 77", null, null);
        insertStore(2L, "없는주소 1", null, null);
        insertStore(3L, "서울 마포구 월드컵로 1", 37.0, 127.0);
        insertStore(4L, "서울 용산구 한강대로 1", null, null);
        given(kakaoLocalClient.geocode("서울 성동구 성수이로 77"))
                .willReturn(new GeocodeResult.Found(new GeoPoint(37.5445, 127.0558)));
        given(kakaoLocalClient.geocode("없는주소 1")).willReturn(new GeocodeResult.NotFound());
        // 서울 용산구 한강대로 1 은 기본값 Unavailable

        assertThat(backfill.fillMissingLocations()).isEqualTo(1);

        assertThat(location(1L)).containsEntry("latitude", 37.5445).containsEntry("longitude", 127.0558);
        assertThat(location(2L)).containsEntry("latitude", null);
        assertThat(location(4L)).containsEntry("latitude", null);
        // 이미 좌표가 있는 가게는 카카오를 부르지 않는다
        verify(kakaoLocalClient, never()).geocode("서울 마포구 월드컵로 1");
        assertThat(location(3L)).containsEntry("latitude", 37.0);
    }

    @Test
    void 폐업한_가게는_채우지_않는다() {
        insertStore(1L, "서울 성동구 성수이로 77", null, null);
        jdbcTemplate.update("UPDATE store.store SET deleted_at = now() WHERE id = 1");

        assertThat(backfill.fillMissingLocations()).isZero();

        verify(kakaoLocalClient, never()).geocode(any());
    }

    @Test
    void 그_사이_주소가_바뀌었거나_좌표가_채워졌으면_덮어쓰지_않는다() {
        insertStore(1L, "서울 용산구 한강대로 1", null, null);
        insertStore(2L, "서울 성동구 성수이로 77", 37.1, 127.1);

        // 변환에 쓴 주소와 지금 주소가 다름
        assertThat(storeRepository.fillLocation(1L, "서울 성동구 성수이로 77", 37.5, 127.0)).isZero();
        // 이미 좌표가 있음
        assertThat(storeRepository.fillLocation(2L, "서울 성동구 성수이로 77", 37.5, 127.0)).isZero();

        assertThat(location(1L)).containsEntry("latitude", null);
        assertThat(location(2L)).containsEntry("latitude", 37.1);
    }

    private void insertStore(long ownerId, String address, Double lat, Double lng) {
        jdbcTemplate.update("""
                INSERT INTO store.store (owner_id, name, category, address, min_order_amount, delivery_fee, latitude, longitude)
                VALUES (?, ?, 'SNACK', ?, 12000, 3000, ?, ?)
                """, ownerId, "가게" + ownerId, address, lat, lng);
    }

    private Map<String, Object> location(long storeId) {
        return jdbcTemplate.queryForMap("SELECT latitude, longitude FROM store.store WHERE id = ?", storeId);
    }
}
