package com.baedalsak.delivery.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 목록 응답 {content[], page, size, totalElements, totalPages}. page 는 0부터 (설계서 'API 명세' 공통 규칙)
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
