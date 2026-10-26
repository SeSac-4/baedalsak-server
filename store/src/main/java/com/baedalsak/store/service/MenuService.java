package com.baedalsak.store.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.store.domain.Menu;
import com.baedalsak.store.dto.request.MenuUpsertRequest;
import com.baedalsak.store.dto.request.SaleStatusRequest;
import com.baedalsak.store.dto.response.MenuResponse;
import com.baedalsak.store.error.StoreErrorCode;
import com.baedalsak.store.repository.MenuRepository;
import com.baedalsak.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 메뉴 관리·조회 (2.1.1 · 2.2.1). 삭제는 soft delete 이고, 삭제된 메뉴는 조회·수정 대상에서 빠진다.
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;
    private final StoreRepository storeRepository;
    private final StoreOwnership storeOwnership;
    private final Clock clock;

    // 삭제된 메뉴는 빼고, 품절은 saleStatus=SOLD_OUT 으로 포함한다
    @Transactional(readOnly = true)
    public List<MenuResponse> getMenus(Long storeId) {
        if (!storeRepository.existsById(storeId)) {
            throw new BusinessException(StoreErrorCode.STORE_NOT_FOUND);
        }
        return menuRepository.findAllByStoreIdAndDeletedAtIsNullOrderById(storeId).stream()
                .map(MenuResponse::from)
                .toList();
    }

    // 등록 직후 판매 중
    @Transactional
    public MenuResponse create(LoginMember member, Long storeId, MenuUpsertRequest request) {
        storeOwnership.getOwnedStore(member, storeId);
        Menu menu = menuRepository.save(
                Menu.create(storeId, request.name(), request.description(), request.price()));
        return MenuResponse.from(menu);
    }

    @Transactional
    public MenuResponse update(LoginMember member, Long storeId, Long menuId, MenuUpsertRequest request) {
        Menu menu = getOwnedMenu(member, storeId, menuId);
        menu.update(request.name(), request.description(), request.price());
        return MenuResponse.from(menu);
    }

    @Transactional
    public void delete(LoginMember member, Long storeId, Long menuId) {
        Menu menu = getOwnedMenu(member, storeId, menuId);
        menu.delete(OffsetDateTime.now(clock));
    }

    @Transactional
    public MenuResponse changeSaleStatus(LoginMember member, Long storeId, Long menuId, SaleStatusRequest request) {
        Menu menu = getOwnedMenu(member, storeId, menuId);
        menu.changeSaleStatus(request.saleStatus());
        return MenuResponse.from(menu);
    }

    // 내 가게의 삭제되지 않은 메뉴. 다른 가게 메뉴·삭제된 메뉴는 404 MENU_NOT_FOUND
    private Menu getOwnedMenu(LoginMember member, Long storeId, Long menuId) {
        storeOwnership.getOwnedStore(member, storeId);
        return menuRepository.findByIdAndStoreIdAndDeletedAtIsNull(menuId, storeId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.MENU_NOT_FOUND));
    }
}
