package com.baedalsak.store.dto.response;

import com.baedalsak.store.domain.BusinessHour;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * 요일별 영업시간 한 행. dayOfWeek 는 0=일 … 6=토, 시각은 'HH:mm', 휴무면 null
 */
public record BusinessHourResponse(
        int dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        boolean isClosed
) {

    public static BusinessHourResponse from(BusinessHour hour) {
        return new BusinessHourResponse(hour.getDayOfWeek(), hour.getOpenTime(), hour.getCloseTime(), hour.isClosed());
    }
}
