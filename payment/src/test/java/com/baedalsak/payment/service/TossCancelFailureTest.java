package com.baedalsak.payment.service;

import com.baedalsak.payment.client.toss.TossApiException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토스 결제 취소 실패 분류 (#127). HTTP 상태가 아니라 토스 에러 코드 기준
 */
class TossCancelFailureTest {

    @Test
    void 응답_없음_5xx_일시_오류_코드는_다시_시도() {
        assertThat(TossCancelFailure.of(TossApiException.noResponse(new SocketTimeoutException()))).isEqualTo(TossCancelFailure.RETRY);
        assertThat(TossCancelFailure.of(TossApiException.rejected(502, "UNKNOWN", "x"))).isEqualTo(TossCancelFailure.RETRY);
        assertThat(TossCancelFailure.of(TossApiException.rejected(400, "PROVIDER_ERROR", "x"))).isEqualTo(TossCancelFailure.RETRY);
        assertThat(TossCancelFailure.of(TossApiException.rejected(403, "FORBIDDEN_CONSECUTIVE_REQUEST", "x"))).isEqualTo(TossCancelFailure.RETRY);
        assertThat(TossCancelFailure.of(TossApiException.rejected(409, "IDEMPOTENT_REQUEST_PROCESSING", "x"))).isEqualTo(TossCancelFailure.RETRY);
        // 에러 본문을 읽지 못함 → 결과 모름 (#125 승인 분류와 같음)
        assertThat(TossCancelFailure.of(TossApiException.rejected(400, null, null))).isEqualTo(TossCancelFailure.RETRY);
    }

    @Test
    void 이미_취소됐을_수_있는_코드는_조회_후_판단() {
        assertThat(TossCancelFailure.of(TossApiException.rejected(400, "ALREADY_CANCELED_PAYMENT", "x"))).isEqualTo(TossCancelFailure.CHECK);
        assertThat(TossCancelFailure.of(TossApiException.rejected(403, "NOT_CANCELABLE_PAYMENT", "x"))).isEqualTo(TossCancelFailure.CHECK);
        assertThat(TossCancelFailure.of(TossApiException.rejected(400, "ALREADY_REFUNDING_PAYMENT", "x"))).isEqualTo(TossCancelFailure.CHECK);
    }

    @Test
    void 그_밖의_에러_응답은_확정_거절() {
        assertThat(TossCancelFailure.of(TossApiException.rejected(400, "INVALID_REQUEST", "x"))).isEqualTo(TossCancelFailure.REJECTED);
        assertThat(TossCancelFailure.of(TossApiException.rejected(404, "NOT_FOUND_PAYMENT", "x"))).isEqualTo(TossCancelFailure.REJECTED);
    }
}
