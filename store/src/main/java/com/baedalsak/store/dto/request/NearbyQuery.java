package com.baedalsak.store.dto.request;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.store.domain.GeoPoint;
import com.baedalsak.store.domain.NearbyArea;

import java.util.List;

/**
 * 가게 목록 · 통계의 위치 파라미터 (lat · lng · radius, #210). 셋 다 선택이다.
 * - lat · lng 둘 다 있으면 반경 안 가게만 (radius 기본 3000m · 최대 10000m)
 * - 셋 다 없으면 위치 조건 없음 (위치 권한을 거부한 고객, 기존 응답과 같음)
 * - 그 외(하나만 · 범위 밖 · radius 만)는 400 VALIDATION_FAILED
 *
 * @param lat    고객 위도 (-90 ~ 90)
 * @param lng    고객 경도 (-180 ~ 180)
 * @param radius 반경 m (1 ~ 10000)
 */
public record NearbyQuery(Double lat, Double lng, Integer radius) {

    public static final int DEFAULT_RADIUS_M = 3000;
    public static final int MAX_RADIUS_M = 10000;

    /**
     * @return 반경 조회 영역. 위치를 보내지 않았으면 null
     * @throws BusinessException 400 VALIDATION_FAILED
     */
    public NearbyArea toArea() {
        if (lat == null && lng == null) {
            if (radius != null) {
                throw invalid("radius", "반경은 위도 · 경도와 함께 보내 주세요.");
            }
            return null;
        }
        if (lat == null || lng == null) {
            throw invalid(lat == null ? "lat" : "lng", "위도와 경도를 함께 보내 주세요.");
        }
        // NaN · Infinity 도 범위 밖으로 처리한다
        if (!(lat >= -90 && lat <= 90)) {
            throw invalid("lat", "위도는 -90 ~ 90 사이여야 합니다.");
        }
        if (!(lng >= -180 && lng <= 180)) {
            throw invalid("lng", "경도는 -180 ~ 180 사이여야 합니다.");
        }
        int radiusM = radius == null ? DEFAULT_RADIUS_M : radius;
        if (radiusM < 1 || radiusM > MAX_RADIUS_M) {
            throw invalid("radius", "반경은 1 ~ " + MAX_RADIUS_M + "m 사이여야 합니다.");
        }
        return new NearbyArea(new GeoPoint(lat, lng), radiusM);
    }

    private static BusinessException invalid(String field, String reason) {
        return new BusinessException(CommonErrorCode.VALIDATION_FAILED, List.of(new FieldErrorDetail(field, reason)));
    }
}
