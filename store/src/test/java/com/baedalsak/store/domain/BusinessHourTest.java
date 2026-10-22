package com.baedalsak.store.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessHourTest {

    @Test
    void 요일은_0_일요일부터_6_토요일로_저장한다() {
        assertThat(BusinessHour.defaultOf(1L, DayOfWeek.SUNDAY).getDayOfWeek()).isEqualTo((short) 0);
        assertThat(BusinessHour.defaultOf(1L, DayOfWeek.MONDAY).getDayOfWeek()).isEqualTo((short) 1);
        assertThat(BusinessHour.defaultOf(1L, DayOfWeek.SATURDAY).getDayOfWeek()).isEqualTo((short) 6);
    }

    @Test
    void 저장값을_다시_요일로_바꾼다() {
        for (DayOfWeek day : DayOfWeek.values()) {
            assertThat(BusinessHour.defaultOf(1L, day).getDay()).isEqualTo(day);
        }
    }

    @Test
    void 휴무일은_시간을_비운다() {
        BusinessHour hour = BusinessHour.closedDay(1L, DayOfWeek.MONDAY);

        assertThat(hour.isClosed()).isTrue();
        assertThat(hour.getOpenTime()).isNull();
        assertThat(hour.getCloseTime()).isNull();
    }

    @Test
    void 기본_영업시간은_9시부터_22시() {
        BusinessHour hour = BusinessHour.defaultOf(1L, DayOfWeek.FRIDAY);

        assertThat(hour.getOpenTime()).isEqualTo(BusinessHour.DEFAULT_OPEN_TIME);
        assertThat(hour.getCloseTime()).isEqualTo(BusinessHour.DEFAULT_CLOSE_TIME);
    }
}
