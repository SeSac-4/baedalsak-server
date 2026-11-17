package com.baedalsak.delivery.dto;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;

import java.util.Locale;

/**
 * 배차 대기 목록 정렬 (GET /v1/deliveries/waiting?sort=).
 */
public enum WaitingSort {
    /** 배달을 빨리 끝낼 수 있는 순 (기본): max(조리 완료까지, 가게까지 이동) + 배달 이동, 작은 순 */
    FASTEST,
    /** 배달비 높은 순 */
    FEE;

    /**
     * @param value 쿼리 값 (대소문자 무시). 비어 있으면 FASTEST
     * @return 정렬
     * @throws BusinessException 모르는 값이면 400 INVALID_INPUT
     */
    public static WaitingSort parse(String value) {
        if (value == null || value.isBlank()) {
            return FASTEST;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED, "sort 는 FASTEST · FEE 중 하나여야 합니다.");
        }
    }
}
