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

    /**
     * 메모리에서 정렬한 전체 목록을 잘라 한 페이지로 만든다.
     *
     * @param all  정렬된 전체 목록
     * @param page 페이지 번호 (0부터)
     * @param size 페이지 크기
     * @return 페이지 응답
     */
    public static <T> PageResponse<T> slice(List<T> all, int page, int size) {
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = (all.size() + size - 1) / size;
        return new PageResponse<>(List.copyOf(all.subList(from, to)), page, size, all.size(), totalPages);
    }

    public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
