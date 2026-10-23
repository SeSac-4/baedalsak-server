package com.baedalsak.store.service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.ErrorCode;
import com.baedalsak.store.domain.BusinessHour;
import com.baedalsak.store.domain.BusinessHourId;
import com.baedalsak.store.domain.Store;
import com.baedalsak.store.dto.response.MenuInternalResponse;
import com.baedalsak.store.dto.response.StoreInternalResponse;
import com.baedalsak.store.repository.BusinessHourRepository;
import com.baedalsak.store.repository.MenuRepository;
import com.baedalsak.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 서비스 간 [내부] 조회. order 가 쓰기 시점 검증(장바구니 담기·주문 생성)에 호출한다 (설계서 '동기 호출' 탭).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreInternalService {

    private final StoreRepository storeRepository;
    private final BusinessHourRepository businessHourRepository;
    private final MenuRepository menuRepository;
    private final Clock clock;

    // 영업 여부는 가게 목록·상세와 같은 규칙 (정상 운영 AND 오늘 영업시간 안)
    public StoreInternalResponse getStore(Long storeId) {
        Store store = getExistingStore(storeId);
        LocalDateTime now = LocalDateTime.now(clock);
        BusinessHour todayHour = businessHourRepository
                .findById(new BusinessHourId(storeId, BusinessHour.toDbValue(now.getDayOfWeek())))
                .orElseThrow();
        return StoreInternalResponse.of(store, store.isOpenAt(todayHour, now.toLocalTime()));
    }

    // 요청한 id 중 없거나 삭제된 메뉴는 응답에서 빠진다 → 호출자가 MENU_NOT_ON_SALE 로 처리
    public List<MenuInternalResponse> getMenus(Long storeId, List<Long> menuIds) {
        getExistingStore(storeId);
        return menuRepository.findAllByStoreIdAndIdInAndDeletedAtIsNull(storeId, menuIds).stream()
                .map(MenuInternalResponse::from)
                .toList();
    }

    private Store getExistingStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }
}
