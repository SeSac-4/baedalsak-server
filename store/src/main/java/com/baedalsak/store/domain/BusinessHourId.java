package com.baedalsak.store.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * business_hour 복합 키 (store_id, day_of_week)
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class BusinessHourId implements Serializable {

    private Long storeId;
    private Short dayOfWeek;
}
