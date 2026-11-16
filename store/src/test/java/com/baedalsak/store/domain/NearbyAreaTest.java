package com.baedalsak.store.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 거리 계산과 반경 조회의 위도 · 경도 범위 (#210)
 */
class NearbyAreaTest {

    // 위도 1도의 거리 ≈ 111195m (지구 반지름 6371000m)
    static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_000;

    @Test
    void 서울시청에서_강남역까지_직선거리는_약_9km() {
        GeoPoint cityHall = new GeoPoint(37.5665, 126.9780);
        GeoPoint gangnam = new GeoPoint(37.4979, 127.0276);

        assertThat(cityHall.distanceTo(gangnam)).isCloseTo(8_793, within(10.0));
        assertThat(gangnam.distanceTo(cityHall)).isEqualTo(cityHall.distanceTo(gangnam));
    }

    @Test
    void 같은_좌표면_거리_0() {
        GeoPoint point = new GeoPoint(37.5, 127.0);

        assertThat(point.distanceTo(point)).isZero();
    }

    @Test
    void 위도_범위는_반경만큼_위아래() {
        NearbyArea area = new NearbyArea(new GeoPoint(37.5, 127.0), 3000);

        assertThat(area.maxLat() - 37.5).isCloseTo(3000 / METERS_PER_DEGREE, within(1e-9));
        assertThat(37.5 - area.minLat()).isCloseTo(3000 / METERS_PER_DEGREE, within(1e-9));
    }

    @Test
    void 경도_범위는_위도가_높을수록_넓다() {
        // 북위 37.5도에서 경도 1도는 위도 1도의 cos(37.5°) ≈ 0.793 배 → 같은 거리면 경도 범위가 더 넓다
        NearbyArea area = new NearbyArea(new GeoPoint(37.5, 127.0), 3000);

        double expected = 3000 / (METERS_PER_DEGREE * Math.cos(Math.toRadians(37.5)));
        assertThat(area.maxLng() - 127.0).isCloseTo(expected, within(1e-9));
        assertThat(area.maxLng() - 127.0).isGreaterThan(area.maxLat() - 37.5);
    }

    @Test
    void 범위_가장자리_동쪽_점까지의_거리가_반경과_같다() {
        NearbyArea area = new NearbyArea(new GeoPoint(37.5, 127.0), 3000);

        assertThat(area.center().distanceTo(new GeoPoint(37.5, area.maxLng()))).isCloseTo(3000, within(1.0));
        assertThat(area.center().distanceTo(new GeoPoint(area.maxLat(), 127.0))).isCloseTo(3000, within(1.0));
    }

    @Test
    void 극에서는_경도_전체() {
        NearbyArea area = new NearbyArea(new GeoPoint(90, 0), 3000);

        assertThat(area.minLng()).isLessThanOrEqualTo(-180);
        assertThat(area.maxLng()).isGreaterThanOrEqualTo(180);
    }
}
