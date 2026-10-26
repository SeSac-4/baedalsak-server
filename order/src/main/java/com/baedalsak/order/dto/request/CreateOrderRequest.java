package com.baedalsak.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주문 생성 요청 (POST /v1/orders). 주문 항목은 장바구니에서 가져오고, 배달 주소·동과 요청사항만 받는다 (4.1.1, 화면 U-04).
 */
public record CreateOrderRequest(
        @NotBlank(message = "배달 주소를 입력해 주세요.")
        @Size(max = 255, message = "배달 주소는 255자 이하로 입력해 주세요.")
        String deliveryAddress,

        // 배달지 동 (예: 성수동2가). 주소 검색 결과의 동을 함께 보낸다 — 배차 대기 목록에 동 단위로 보여 줌 (6.2.1)
        @NotBlank(message = "배달지 동을 입력해 주세요.")
        @Size(max = 50, message = "배달지 동은 50자 이하로 입력해 주세요.")
        String deliveryDong,

        // 가게 요청사항 (선택)
        @Size(max = 200, message = "가게 요청사항은 200자 이하로 입력해 주세요.")
        String storeRequest,

        // 라이더 요청사항 (선택)
        @Size(max = 200, message = "라이더 요청사항은 200자 이하로 입력해 주세요.")
        String riderRequest
) {
}
