package com.baedalsak.payment.error;

import com.baedalsak.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * payment 도메인 에러 코드 (설계서 'API 명세' 에러표). 공통 코드는 CommonErrorCode 를 쓴다.
 * ORDER_NOT_FOUND·ORDER_INVALID_STATUS 는 order 내부 API 검증 결과를 payment 가 응답할 때 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(404, "주문을 찾을 수 없습니다."),
    ORDER_INVALID_STATUS(409, "결제할 수 없는 주문 상태입니다."),
    PAYMENT_NOT_FOUND(404, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(400, "결제 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_ALREADY_APPROVED(409, "이미 승인된 결제입니다."),
    // #125: 토스가 승인을 확정 거절함. 응답 message 는 토스 메시지로 바꿔 보낸다 (PaymentExceptionHandler), 이 문구는 토스 메시지가 없을 때만
    PAYMENT_CONFIRM_REJECTED(400, "결제가 승인되지 않았습니다."),
    // #125: 이전 paymentKey 의 결과를 아직 모르는 동안 다른 키 승인을 거절
    PAYMENT_KEY_MISMATCH(409, "이전 결제를 확인하고 있습니다. 잠시 후 다시 시도해 주세요."),
    // #128: 같은 고객의 결제가 승인 진행 중(paymentKey 저장됨)이라 새 결제 요청을 만들 수 없음 (고객당 진행 중 결제 1건)
    PAYMENT_IN_PROGRESS(409, "이전 결제를 확인하고 있어요"),
    // #128: 결제 요청됨이 아니라 승인할 수 없음 (만료된 결제 등)
    PAYMENT_INVALID_STATUS(409, "결제할 수 없는 결제 상태입니다."),
    // 문구는 설계서 '화면 흐름' 탭 U-06 비고
    PAYMENT_GATEWAY_ERROR(502, "결제 확인 중 오류. 다시 시도해 주세요");

    private final int status;
    private final String message;
}
