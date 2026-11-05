package com.baedalsak.payment.service;

import com.baedalsak.payment.client.toss.TossApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토스 승인 실패 분류 (#125). HTTP 상태가 아니라 토스 에러 코드 기준
 */
class TossConfirmFailureTest {

    @Test
    void 응답이_없으면_결과_모름() {
        assertThat(TossConfirmFailure.of(TossApiException.noResponse(new SocketTimeoutException())))
                .isEqualTo(TossConfirmFailure.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({
            // 5xx 는 코드와 관계없이 결과 모름
            "500, FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING, UNKNOWN",
            "500, FAILED_INTERNAL_SYSTEM_PROCESSING, UNKNOWN",
            // 4xx 중 결과를 모르는 코드
            "400, PROVIDER_ERROR, UNKNOWN",
            "409, ALREADY_PROCESSING_REQUEST, UNKNOWN",
            "409, IDEMPOTENT_REQUEST_PROCESSING, UNKNOWN",
            // 이미 처리됨 → 조회로 확인
            "400, ALREADY_PROCESSED_PAYMENT, ALREADY_PROCESSED",
            // 그 외 → 확정 실패
            "403, REJECT_CARD_COMPANY, REJECTED",
            "400, CARD_PROCESSING_ERROR, REJECTED",
            "404, NOT_FOUND_PAYMENT_SESSION, REJECTED",
            "400, EXCEED_MAX_DAILY_PAYMENT_COUNT, REJECTED"
    })
    void 토스_에러_코드로_분류한다(int status, String code, TossConfirmFailure expected) {
        assertThat(TossConfirmFailure.of(TossApiException.rejected(status, code, "메시지"))).isEqualTo(expected);
    }

    @Test
    void 에러_코드를_읽지_못한_4xx는_결과_모름() {
        assertThat(TossConfirmFailure.of(TossApiException.rejected(400, null, null)))
                .isEqualTo(TossConfirmFailure.UNKNOWN);
    }
}
