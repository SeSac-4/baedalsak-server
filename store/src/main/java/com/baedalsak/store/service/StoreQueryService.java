package com.baedalsak.store.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.store.config.ImageProperties;
import com.baedalsak.store.domain.BusinessHour;
import com.baedalsak.store.domain.Category;
import com.baedalsak.store.domain.Store;
import com.baedalsak.store.dto.response.CategoryResponse;
import com.baedalsak.store.dto.response.CursorResponse;
import com.baedalsak.store.dto.response.StoreDetailResponse;
import com.baedalsak.store.dto.response.StoreListSummaryResponse;
import com.baedalsak.store.dto.response.StoreSummaryResponse;
import com.baedalsak.store.error.StoreErrorCode;
import com.baedalsak.store.repository.BusinessHourRepository;
import com.baedalsak.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
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
    private final ImageProperties imageProperties;
    private final Clock clock;

    public List<CategoryResponse> getCategories() {
        return Arrays.stream(Category.values())
                .map(CategoryResponse::from)
                .toList();
    }

    // 카테고리가 없으면 전체 가게. id 순 커서 조회로, size + 1 개를 읽어 다음 페이지 여부를 판단한다 (count 쿼리 없음)
    // 오늘 영업시간은 그 페이지 가게만 한 번에 조회한다
    public CursorResponse<StoreSummaryResponse> getStores(Category category, Long cursor, int size) {
        long after = cursor == null ? 0L : cursor;
        Limit limit = Limit.of(size + 1);
        List<Store> fetched = category == null
                ? storeRepository.findByIdGreaterThanOrderByIdAsc(after, limit)
                : storeRepository.findByCategoryAndIdGreaterThanOrderByIdAsc(category, after, limit);
        if (fetched.isEmpty()) {
            return CursorResponse.last(List.of());
        }

        boolean hasNext = fetched.size() > size;
        List<Store> stores = hasNext ? fetched.subList(0, size) : fetched;

        LocalDateTime now = LocalDateTime.now(clock);
        short today = BusinessHour.toDbValue(now.getDayOfWeek());
        Map<Long, BusinessHour> todayHours = businessHourRepository
                .findAllByStoreIdInAndDayOfWeek(stores.stream().map(Store::getId).toList(), today).stream()
                .collect(Collectors.toMap(BusinessHour::getStoreId, Function.identity()));

        List<StoreSummaryResponse> content = stores.stream()
                .map(store -> StoreSummaryResponse.of(store, todayHours.get(store.getId()), now.toLocalTime(),
                        imageProperties))
                .toList();
        if (!hasNext) {
            return CursorResponse.last(content);
        }
        return CursorResponse.next(content, String.valueOf(stores.getLast().getId()));
    }

    // 가게 목록 통계 (#169). 카테고리별 집계 1번 + 영업 중 수 1번, 가게 수와 무관하게 쿼리 2개
    public StoreListSummaryResponse getStoreListSummary() {
        Map<Category, Long> categoryCounts = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            categoryCounts.put(category, 0L);
        }
        long totalCount = 0;
        Integer minDeliveryFee = null;
        for (Object[] row : storeRepository.countAndMinDeliveryFeeByCategory()) {
            long count = (Long) row[1];
            int fee = (Integer) row[2];
            categoryCounts.put((Category) row[0], count);
            totalCount += count;
            minDeliveryFee = minDeliveryFee == null ? fee : Math.min(minDeliveryFee, fee);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        long openCount = storeRepository.countOpenAt(BusinessHour.toDbValue(now.getDayOfWeek()), now.toLocalTime());
        return new StoreListSummaryResponse(totalCount, openCount, minDeliveryFee, categoryCounts);
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
        return StoreDetailResponse.of(store, hours, LocalDateTime.now(clock), imageProperties);
    }
}
