package com.baedalsak.store.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 가게 카테고리 (1.5.1). 고정 6개라 테이블 없이 enum + DB CHECK 로 관리한다.
 */
@Getter
@RequiredArgsConstructor
public enum Category {
    KOREAN("한식"),
    SNACK("분식"),
    CHICKEN("치킨"),
    CHINESE("중식"),
    PIZZA("피자"),
    CAFE_DESSERT("카페·디저트");

    private final String label;
}
