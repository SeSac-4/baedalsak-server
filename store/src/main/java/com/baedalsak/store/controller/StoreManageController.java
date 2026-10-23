package com.baedalsak.store.controller;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.store.dto.request.BusinessHoursRequest;
import com.baedalsak.store.dto.request.OperationStatusRequest;
import com.baedalsak.store.dto.request.StoreUpsertRequest;
import com.baedalsak.store.dto.response.StoreDetailResponse;
import com.baedalsak.store.service.StoreManageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사장님 가게 관리 API. gateway 가 /v1/stores 이하를 이 서비스로 보낸다.
 */
@RestController
@RequestMapping("/v1/stores")
@RequiredArgsConstructor
public class StoreManageController {

    private final StoreManageService storeManageService;

    // 1.2.1 가게 등록. 이미 가게가 있으면 409 STORE_ALREADY_EXISTS
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreDetailResponse create(@CurrentMember LoginMember member,
                                      @Valid @RequestBody StoreUpsertRequest request) {
        return storeManageService.create(member, request);
    }

    // 1.2.1 가게 수정
    @PutMapping("/{storeId}")
    public StoreDetailResponse update(@CurrentMember LoginMember member, @PathVariable Long storeId,
                                      @Valid @RequestBody StoreUpsertRequest request) {
        return storeManageService.update(member, storeId, request);
    }

    // 1.4.1 운영 상태 변경 (정상 운영 ↔ 임시 중지)
    @PatchMapping("/{storeId}/status")
    public StoreDetailResponse changeOperationStatus(@CurrentMember LoginMember member, @PathVariable Long storeId,
                                                     @Valid @RequestBody OperationStatusRequest request) {
        return storeManageService.changeOperationStatus(member, storeId, request);
    }

    // 1.4.2 영업시간 설정 (7개 요일 한 번에 교체)
    @PutMapping("/{storeId}/business-hours")
    public StoreDetailResponse replaceBusinessHours(@CurrentMember LoginMember member, @PathVariable Long storeId,
                                                    @Valid @RequestBody BusinessHoursRequest request) {
        return storeManageService.replaceBusinessHours(member, storeId, request);
    }
}
