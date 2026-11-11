package com.baedalsak.store.dto.response;

import java.util.List;

/**
 * 커서 목록 응답 {content[], nextCursor, hasNext} (#169).
 * nextCursor 는 다음 요청의 cursor 로 그대로 넣는 값이다. 프론트는 해석하지 않는다 (정렬 기준이 늘어도 모양 유지).
 * 마지막 페이지면 nextCursor = null, hasNext = false
 */
public record CursorResponse<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext
) {

    public static <T> CursorResponse<T> last(List<T> content) {
        return new CursorResponse<>(content, null, false);
    }

    public static <T> CursorResponse<T> next(List<T> content, String nextCursor) {
        return new CursorResponse<>(content, nextCursor, true);
    }
}
