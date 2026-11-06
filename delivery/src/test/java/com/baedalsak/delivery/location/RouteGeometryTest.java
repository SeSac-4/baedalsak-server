package com.baedalsak.delivery.location;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 경로 계산: 거리, 경로 위 위치, 남은 거리
 */
class RouteGeometryTest {

    // 위도 0.001도 ≈ 111.2m. 남쪽으로 두 구간 → ㄱ자로 동쪽 한 구간
    private static final GeoPoint A = new GeoPoint(37.5450, 127.0560);
    private static final GeoPoint B = new GeoPoint(37.5440, 127.0560);
    private static final GeoPoint C = new GeoPoint(37.5430, 127.0560);
    private static final GeoPoint D = new GeoPoint(37.5430, 127.0570);
    private static final List<GeoPoint> PATH = List.of(A, B, C, D);

    @Test
    void distanceOfOneThousandthDegreeLatitudeIsAbout111m() {
        assertThat(RouteGeometry.distanceM(A, B)).isCloseTo(111.2, within(0.5));
    }

    @Test
    void lengthSumsAllSegments() {
        double expected = RouteGeometry.distanceM(A, B) + RouteGeometry.distanceM(B, C) + RouteGeometry.distanceM(C, D);
        assertThat(RouteGeometry.lengthM(PATH)).isCloseTo(expected, within(0.001));
    }

    @Test
    void pointAlongInterpolatesInsideSegment() {
        GeoPoint half = RouteGeometry.pointAlong(PATH, RouteGeometry.distanceM(A, B) / 2);

        assertThat(half.lat()).isCloseTo(37.5445, within(1e-7));
        assertThat(half.lng()).isCloseTo(127.0560, within(1e-7));
    }

    @Test
    void pointAlongCrossesIntoNextSegments() {
        double toC = RouteGeometry.distanceM(A, B) + RouteGeometry.distanceM(B, C);
        GeoPoint onLast = RouteGeometry.pointAlong(PATH, toC + RouteGeometry.distanceM(C, D) / 2);

        assertThat(onLast.lat()).isCloseTo(37.5430, within(1e-7));
        assertThat(onLast.lng()).isCloseTo(127.0565, within(1e-7));
    }

    @Test
    void pointAlongClampsToStartAndEnd() {
        assertThat(RouteGeometry.pointAlong(PATH, -10)).isEqualTo(A);
        assertThat(RouteGeometry.pointAlong(PATH, 0)).isEqualTo(A);
        assertThat(RouteGeometry.pointAlong(PATH, 1_000_000)).isEqualTo(D);
    }

    @Test
    void remainingIsFullLengthAtStartAndZeroAtEnd() {
        assertThat(RouteGeometry.remainingM(PATH, A)).isCloseTo(RouteGeometry.lengthM(PATH), within(0.5));
        assertThat(RouteGeometry.remainingM(PATH, D)).isCloseTo(0, within(0.5));
    }

    @Test
    void remainingMatchesPointAlong() {
        double travelled = 150;
        GeoPoint current = RouteGeometry.pointAlong(PATH, travelled);

        assertThat(RouteGeometry.remainingM(PATH, current))
                .isCloseTo(RouteGeometry.lengthM(PATH) - travelled, within(1.0));
    }

    @Test
    void remainingSnapsOffRoadPositionToNearestSegment() {
        // B 에서 서쪽으로 약 9m 벗어난 GPS → B 에 있는 것으로 본다
        GeoPoint offRoad = new GeoPoint(37.5440, 127.0559);

        double fromB = RouteGeometry.distanceM(B, C) + RouteGeometry.distanceM(C, D);
        assertThat(RouteGeometry.remainingM(PATH, offRoad)).isCloseTo(fromB, within(1.0));
    }

    @Test
    void remainingIgnoresSegmentsAlreadyPassedOnLoopingRoute() {
        // ㄷ자 경로: 동쪽 → 남쪽 짧게 → 서쪽으로 되돌아옴. 되돌아오는 길은 처음 길과 약 11m 떨어져 있다
        GeoPoint start = new GeoPoint(37.5450, 127.0550);
        GeoPoint east = new GeoPoint(37.5450, 127.0570);
        GeoPoint turn = new GeoPoint(37.5449, 127.0570);
        GeoPoint end = new GeoPoint(37.5449, 127.0550);
        List<GeoPoint> loop = List.of(start, east, turn, end);
        double length = RouteGeometry.lengthM(loop);
        // 되돌아오는 길을 달리는 중인데 GPS 가 처음 길 쪽으로 튐 (처음 길 약 3m, 되돌아오는 길 약 8m)
        GeoPoint returning = new GeoPoint(37.54497, 127.0560);
        double passed = RouteGeometry.distanceM(start, east) + RouteGeometry.distanceM(east, turn);

        double naive = RouteGeometry.remainingM(loop, returning);
        double guided = RouteGeometry.remainingM(loop, returning, passed - 50);

        double expected = RouteGeometry.distanceM(turn, end) / 2;
        assertThat(naive).isGreaterThan(length / 2);              // 처음 길에 붙어 남은 거리가 크게 나온다
        assertThat(guided).isCloseTo(expected, within(3.0));      // 지나온 구간을 빼면 정확하다
    }

    @Test
    void remainingIsZeroWhenStartBeyondRoute() {
        assertThat(RouteGeometry.remainingM(PATH, D, 1_000_000)).isZero();
    }
}
