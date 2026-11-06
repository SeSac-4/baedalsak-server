package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.location.GeoPoint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /v1/deliveries/{deliveryId}/location 요청 (라이더 앱 실제 GPS).
 *
 * @param lat 위도
 * @param lng 경도
 */
public record LocationReportRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng
) {
    public GeoPoint toPoint() {
        return new GeoPoint(lat, lng);
    }
}
