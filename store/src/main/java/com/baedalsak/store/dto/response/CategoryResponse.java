package com.baedalsak.store.dto.response;

import com.baedalsak.store.domain.Category;

/**
 * 카테고리 (GET /v1/categories 응답 항목). code 는 가게의 category 값, name 은 화면 표시용 이름
 */
public record CategoryResponse(Category code, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category, category.getLabel());
    }
}
