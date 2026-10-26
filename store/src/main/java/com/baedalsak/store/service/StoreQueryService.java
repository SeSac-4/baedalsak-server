package com.baedalsak.store.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.store.domain.BusinessHour;
import com.baedalsak.store.domain.Category;
import com.baedalsak.store.domain.Store;
import com.baedalsak.store.dto.response.CategoryResponse;
import com.baedalsak.store.dto.response.StoreDetailResponse;
import com.baedalsak.store.dto.response.StoreSummaryResponse;
import com.baedalsak.store.error.StoreErrorCode;
import com.baedalsak.store.repository.BusinessHourRepository;
import com.baedalsak.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 카테고리·가게 조회 (1.5.1 · 1.1.1 · 1.3.1 · 8.3.1). 영업 여부는 한국 시각 기준으로 계산한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

    private final StoreRepository storeRepository;
    private final BusinessHourRepository businessHourRepository;
    private final Clock clock;

    public List<CategoryResponse> getCategories() {
        return Arrays.stream(Category.values())
                .map(CategoryResponse::from)
                .toList();
    }

    // 카테고리가 없으면 전체 가게. 오늘 영업시간은 한 번에 조회한다
    public List<StoreSummaryResponse> getStores(Category category) {
        Sort byId = Sort.by("id");
        List<Store> stores = category == null
                ? storeRepository.findAll(byId)
                : storeRepository.findAllByCategory(category, byId);
        if (stores.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        short today = BusinessHour.toDbValue(now.getDayOfWeek());
        Map<Long, BusinessHour> todayHours = businessHourRepository
                .findAllByStoreIdInAndDayOfWeek(stores.stream().map(Store::getId).toList(), today).stream()
                .collect(Collectors.toMap(BusinessHour::getStoreId, Function.identity()));

        return stores.stream()
                .map(store -> StoreSummaryResponse.of(store, todayHours.get(store.getId()), now.toLocalTime()))
                .toList();
    }

    public StoreDetailResponse getStore(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
        return toDetail(store);
    }

    // 아직 가게를 등록하지 않았으면 404 STORE_NOT_FOUND → 프론트가 가게 등록 화면(O-00)으로 안내
    public StoreDetailResponse getMyStore(LoginMember member) {
        Store store = storeRepository.findByOwnerId(member.id())
                .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
        return toDetail(store);
    }

    private StoreDetailResponse toDetail(Store store) {
        List<BusinessHour> hours = businessHourRepository.findAllByStoreIdOrderByDayOfWeek(store.getId());
        return StoreDetailResponse.of(store, hours, LocalDateTime.now(clock));
    }
}
