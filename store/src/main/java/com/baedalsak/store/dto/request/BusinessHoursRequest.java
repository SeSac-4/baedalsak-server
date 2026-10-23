package com.baedalsak.store.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * 영업시간 설정 요청 (PUT /v1/stores/{storeId}/business-hours). 7개 요일을 한 번에 교체한다 (1.4.2).
 */
public record BusinessHoursRequest(
        @NotNull(message = "영업시간을 입력해 주세요.")
        @Size(min = 7, max = 7, message = "7개 요일을 모두 입력해 주세요.")
        List<@Valid @NotNull BusinessHourRequest> businessHours
) {

    // 0(일)~6(토)이 한 번씩
    @JsonIgnore
    @AssertTrue(message = "요일(0=일 ~ 6=토)이 한 번씩 있어야 합니다.")
    public boolean isEveryDayOnce() {
        if (businessHours == null || businessHours.size() != 7) {
            return true;   // 개수는 @Size 가 따로 알려 준다
        }
        return businessHours.stream()
                .filter(Objects::nonNull)
                .map(BusinessHourRequest::dayOfWeek)
                .filter(Objects::nonNull)
                .distinct()
                .count() == 7;
    }

    public record BusinessHourRequest(
            @NotNull(message = "요일을 입력해 주세요.")
            @Min(value = 0, message = "요일은 0(일)~6(토)입니다.")
            @Max(value = 6, message = "요일은 0(일)~6(토)입니다.")
            Integer dayOfWeek,

            @JsonFormat(pattern = "HH:mm")
            LocalTime openTime,

            @JsonFormat(pattern = "HH:mm")
            LocalTime closeTime,

            @NotNull(message = "휴무 여부를 입력해 주세요.")
            Boolean isClosed
    ) {

        // 휴무가 아니면 오픈·마감 시각 필수 (API 명세 1.4.2)
        @JsonIgnore
        @AssertTrue(message = "영업일은 오픈 시각과 마감 시각을 입력해 주세요.")
        public boolean isTimeValid() {
            if (isClosed == null || isClosed) {
                return true;
            }
            return openTime != null && closeTime != null;
        }
    }
}
