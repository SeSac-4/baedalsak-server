package com.baedalsak.delivery.dto;

import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;

import java.util.Locale;

/**
 * 배차 대기 목록 정렬 (GET /v1/deliveries/waiting?sort=).
 */
public enum WaitingSort {
    /** 오래 기다린 순 (기본) */
    OLDEST,
    /** 배달을 빨리 끝낼 수 있는 순: max(조리 완료까지, 가게까지 이동) + 배달 이동, 작은 순 */
    FASTEST,
    /** 배달비 높은 순 */
    FEE;

    /**
     * @param value 쿼리 값 (대소문자 무시). 비어 있으면 OLDEST
     * @return 정렬
     * @throws BusinessException 모르는 값이면 400 INVALID_INPUT
     */
    public static WaitingSort parse(String value) {
        if (value == null || value.isBlank()) {
            return OLDEST;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sort 는 OLDEST · FASTEST · FEE 중 하나여야 합니다.");
        }
    }
}
