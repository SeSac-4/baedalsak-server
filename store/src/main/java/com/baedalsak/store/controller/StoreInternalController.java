package com.baedalsak.store.controller;

import com.baedalsak.store.dto.response.MenuInternalResponse;
import com.baedalsak.store.dto.response.StoreInternalResponse;
import com.baedalsak.store.service.StoreInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [내부] 가게·메뉴 조회 API. gateway 라우트가 없어 외부에서는 호출할 수 없고(404), 서비스끼리 K8s DNS 로 직접 호출한다.
 * 인증 헤더 없음 (네트워크 격리).
 */
@RestController
@RequestMapping("/internal/stores/{storeId}")
@RequiredArgsConstructor
public class StoreInternalController {

    private final StoreInternalService storeInternalService;

    // 영업 여부·최소주문금액·배달비·사장님 ID
    @GetMapping
    public StoreInternalResponse getStore(@PathVariable Long storeId) {
        return storeInternalService.getStore(storeId);
    }

    // 메뉴 일괄 조회 (?ids=1,2,3 또는 ?ids=1&ids=2)
    @GetMapping("/menus")
    public List<MenuInternalResponse> getMenus(@PathVariable Long storeId, @RequestParam List<Long> ids) {
        return storeInternalService.getMenus(storeId, ids);
    }
}
