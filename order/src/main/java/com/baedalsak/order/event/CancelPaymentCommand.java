package com.baedalsak.order.event;

/**
 * CancelPayment 명령 본문 (라우팅 키 command.payment.cancel, 설계서 '이벤트 목록' 탭). payment 가 전액 취소한다.
 * 금액·고객은 payment 기록이 기준이라 보내지 않는다. paymentId 는 모르면 null (payment 가 orderId 로 찾는다).
 */
public record CancelPaymentCommand(
        Long orderId,
        Long paymentId,
        String reason,
        String requestedBy
) {

    public static final String EVENT_TYPE = "CancelPayment";
    public static final String ROUTING_KEY = "command.payment.cancel";
    public static final String REQUESTED_BY_OWNER = "OWNER";
    public static final String REQUESTED_BY_CUSTOMER = "CUSTOMER";
    public static final String REQUESTED_BY_SYSTEM = "SYSTEM";
}
