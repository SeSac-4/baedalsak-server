package com.baedalsak.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 요일별 영업시간 (business_hour 테이블). 가게당 7행.
 * day_of_week 는 0=일 … 6=토 로 저장한다 (java.time.DayOfWeek 는 1=월 … 7=일).
 */
@Entity
@Table(name = "business_hour")
@IdClass(BusinessHourId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessHour {

    public static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(9, 0);
    public static final LocalTime DEFAULT_CLOSE_TIME = LocalTime.of(22, 0);

    @Id
    private Long storeId;

    @Id
    private Short dayOfWeek;

    private LocalTime openTime;

    private LocalTime closeTime;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;

    private BusinessHour(Long storeId, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {
        this.storeId = storeId;
        this.dayOfWeek = toDbValue(dayOfWeek);
        this.openTime = closed ? null : openTime;
        this.closeTime = closed ? null : closeTime;
        this.closed = closed;
    }

    public static BusinessHour open(Long storeId, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        return new BusinessHour(storeId, dayOfWeek, openTime, closeTime, false);
    }

    public static BusinessHour closedDay(Long storeId, DayOfWeek dayOfWeek) {
        return new BusinessHour(storeId, dayOfWeek, null, null, true);
    }

    // 가게 등록 시 기본 영업시간 09:00~22:00 (1.2.1)
    public static BusinessHour defaultOf(Long storeId, DayOfWeek dayOfWeek) {
        return open(storeId, dayOfWeek, DEFAULT_OPEN_TIME, DEFAULT_CLOSE_TIME);
    }

    public DayOfWeek getDay() {
        return dayOfWeek == 0 ? DayOfWeek.SUNDAY : DayOfWeek.of(dayOfWeek);
    }

    static short toDbValue(DayOfWeek dayOfWeek) {
        return (short) (dayOfWeek.getValue() % 7);
    }
}
