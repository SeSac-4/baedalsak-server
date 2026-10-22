package com.baedalsak.store.controller;

import com.baedalsak.store.common.auth.CurrentMember;
import com.baedalsak.store.common.auth.LoginMember;
import com.baedalsak.store.dto.request.MenuUpsertRequest;
import com.baedalsak.store.dto.request.SaleStatusRequest;
import com.baedalsak.store.dto.response.MenuResponse;
import com.baedalsak.store.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 메뉴 API. 목록은 인증 불필요, 등록·수정·삭제·판매 상태 변경은 사장님(내 가게).
 */
@RestController
@RequestMapping("/v1/stores/{storeId}/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // 2.2.1 가게 메뉴 목록 (삭제 제외, 품절 포함)
    @GetMapping
    public List<MenuResponse> getMenus(@PathVariable Long storeId) {
        return menuService.getMenus(storeId);
    }

    // 2.1.1 메뉴 등록
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuResponse create(@CurrentMember LoginMember member, @PathVariable Long storeId,
                               @Valid @RequestBody MenuUpsertRequest request) {
        return menuService.create(member, storeId, request);
    }

    // 2.1.1 메뉴 수정
    @PutMapping("/{menuId}")
    public MenuResponse update(@CurrentMember LoginMember member, @PathVariable Long storeId,
                               @PathVariable Long menuId, @Valid @RequestBody MenuUpsertRequest request) {
        return menuService.update(member, storeId, menuId, request);
    }

    // 2.1.1 메뉴 삭제 (soft delete)
    @DeleteMapping("/{menuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentMember LoginMember member, @PathVariable Long storeId, @PathVariable Long menuId) {
        menuService.delete(member, storeId, menuId);
    }

    // 2.1.1 판매 중 ↔ 품절 전환
    @PatchMapping("/{menuId}/status")
    public MenuResponse changeSaleStatus(@CurrentMember LoginMember member, @PathVariable Long storeId,
                                         @PathVariable Long menuId, @Valid @RequestBody SaleStatusRequest request) {
        return menuService.changeSaleStatus(member, storeId, menuId, request);
    }
}
