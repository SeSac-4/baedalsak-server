package com.baedalsak.store.controller;

import com.baedalsak.store.common.auth.CurrentMember;
import com.baedalsak.store.common.auth.LoginMember;
import com.baedalsak.store.domain.Category;
import com.baedalsak.store.dto.response.StoreDetailResponse;
import com.baedalsak.store.dto.response.StoreSummaryResponse;
import com.baedalsak.store.service.StoreQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 가게 조회 API. 목록·상세는 인증 불필요, 내 가게는 사장님.
 */
@RestController
@RequestMapping("/v1/stores")
@RequiredArgsConstructor
public class StoreQueryController {

    private final StoreQueryService storeQueryService;

    // 1.1.1 가게 목록. category 가 없으면 전체, 없는 카테고리 값이면 400
    @GetMapping
    public List<StoreSummaryResponse> getStores(@RequestParam(required = false) Category category) {
        return storeQueryService.getStores(category);
    }

    // 8.3.1 내 가게 조회. /{storeId} 보다 먼저 매칭된다 (경로 리터럴 우선)
    @GetMapping("/me")
    public StoreDetailResponse getMyStore(@CurrentMember LoginMember member) {
        return storeQueryService.getMyStore(member);
    }

    // 1.3.1 가게 상세
    @GetMapping("/{storeId}")
    public StoreDetailResponse getStore(@PathVariable Long storeId) {
        return storeQueryService.getStore(storeId);
    }
}
