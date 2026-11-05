package com.baedalsak.payment.client.toss;

/**
 * 토스 API 호출 실패. 토스가 에러 본문({code, message})으로 답했으면 status·code·tossMessage 가 있고,
 * 연결 실패·응답 시간 초과·빈 응답처럼 답을 받지 못했으면 모두 null 이다 (결과를 모르는 경우).
 * 처리 방법은 HTTP 상태가 아니라 토스 에러 코드로 정한다 (#125).
 */
public class TossApiException extends RuntimeException {

    private final Integer status;
    private final String code;
    private final String tossMessage;

    private TossApiException(String description, Integer status, String code, String tossMessage, Throwable cause) {
        super(description, cause);
        this.status = status;
        this.code = code;
        this.tossMessage = tossMessage;
    }

    // 토스가 에러 본문으로 답함
    public static TossApiException rejected(int status, String code, String tossMessage) {
        return new TossApiException("토스 에러 응답 status=" + status + ", code=" + code, status, code, tossMessage, null);
    }

    // 응답을 받지 못함 (연결 실패·시간 초과·빈 응답)
    public static TossApiException noResponse(Throwable cause) {
        return new TossApiException("토스 응답 없음", null, null, null, cause);
    }

    public boolean isNoResponse() {
        return status == null;
    }

    public Integer getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getTossMessage() {
        return tossMessage;
    }
}
