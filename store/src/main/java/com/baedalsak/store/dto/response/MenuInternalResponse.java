package com.baedalsak.store.dto.response;

import com.baedalsak.store.domain.Menu;
import com.baedalsak.store.domain.SaleStatus;

/**
 * [내부] 메뉴 일괄 조회 응답 항목 (MenuInternal). order 가 가격·판매 상태를 검증하고 금액을 서버에서 계산한다 (N4.7).
 * 설계서('서비스 경계' 탭)는 price·sale_status 만 적지만, 없는 메뉴는 응답에서 빠지므로 id 로 요청과 짝을 맞추고,
 * name 은 장바구니·주문 항목의 menu_name 스냅샷에 쓴다 (둘 다 없으면 order 가 동작하지 않음).
 */
public record MenuInternalResponse(
        Long id,
        String name,
        Integer price,
        SaleStatus saleStatus
) {

    public static MenuInternalResponse from(Menu menu) {
        return new MenuInternalResponse(menu.getId(), menu.getName(), menu.getPrice(), menu.getSaleStatus());
    }
}
