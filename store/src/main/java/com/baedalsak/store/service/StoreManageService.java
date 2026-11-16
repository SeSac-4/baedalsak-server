package com.baedalsak.store.service;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.store.client.OrderClient;
import com.baedalsak.store.config.ImageProperties;
import com.baedalsak.store.domain.BusinessHour;
import com.baedalsak.store.domain.GeoPoint;
import com.baedalsak.store.domain.Store;
import com.baedalsak.store.dto.request.BusinessHoursRequest;
import com.baedalsak.store.dto.request.OperationStatusRequest;
import com.baedalsak.store.dto.request.StoreUpsertRequest;
import com.baedalsak.store.dto.response.StoreDetailResponse;
import com.baedalsak.store.error.StoreErrorCode;
import com.baedalsak.store.repository.BusinessHourRepository;
import com.baedalsak.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 사장님 가게 관리: 등록·수정·운영 상태·영업시간·폐업 (1.2.1 · 1.4.1 · 1.4.2 · #176)
 * 등록 · 수정은 주소를 좌표로 바꿔 함께 저장한다 (#210). 카카오 호출은 트랜잭션 밖에서 한 뒤 TransactionTemplate 으로 저장한다
 * (HTTP 대기 동안 DB 커넥션을 잡지 않음).
 */
@Service
@RequiredArgsConstructor
public class StoreManageService {

    private final StoreRepository storeRepository;
    private final BusinessHourRepository businessHourRepository;
    private final StoreOwnership storeOwnership;
    private final ImageService imageService;
    private final ImageProperties imageProperties;
    private final OrderClient orderClient;
    private final StoreLocator storeLocator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    // 영업시간 7행(09:00~22:00)을 함께 만들고, 운영 상태는 임시 중지로 시작한다.
    // 사진(imageKey)은 받지 않는다 — 가게 id 가 생기기 전이라 업로드 URL 을 받을 수 없음
    // 주소가 카카오에 없으면 400 (가게를 만들지 않음), 카카오를 확인하지 못하면 좌표 없이 만든다
    public StoreDetailResponse create(LoginMember member, StoreUpsertRequest request) {
        storeOwnership.requireOwner(member);
        if (storeRepository.existsByOwnerId(member.id())) {
            throw new BusinessException(StoreErrorCode.STORE_ALREADY_EXISTS);
        }
        GeoPoint location = storeLocator.locate(request.address());
        return transactionTemplate.execute(status -> {
            Store created = Store.create(member.id(), request.name(), request.category(),
                    request.address(), request.minOrderAmount(), request.deliveryFee());
            created.relocate(location);
            Store store;
            try {
                store = storeRepository.saveAndFlush(created);
            } catch (DataIntegrityViolationException e) {
                // 같은 사장님이 동시에 두 번 등록해 existsByOwnerId 를 둘 다 통과한 경우 → store_owner_active_uq 위반
                throw new BusinessException(StoreErrorCode.STORE_ALREADY_EXISTS);
            }
            List<BusinessHour> hours = Arrays.stream(DayOfWeek.values())
                    .map(day -> BusinessHour.defaultOf(store.getId(), day))
                    .toList();
            businessHourRepository.saveAll(hours);
            return toDetail(store);
        });
    }

    // 좌표는 주소가 바뀌었거나 지난번에 변환하지 못했을 때만 다시 변환한다 (그 외에는 카카오를 부르지 않음)
    public StoreDetailResponse update(LoginMember member, Long storeId, StoreUpsertRequest request) {
        Store current = storeOwnership.getOwnedStore(member, storeId);
        imageService.requireOwnKey(storeId, request.imageKey());
        boolean relocate = current.needsGeocoding(request.address());
        GeoPoint location = relocate ? storeLocator.locate(request.address()) : null;
        return transactionTemplate.execute(status -> {
            Store store = storeOwnership.getOwnedStore(member, storeId);
            store.update(request.name(), request.category(), request.address(),
                    request.minOrderAmount(), request.deliveryFee(), request.imageKey());
            if (relocate) {
                store.relocate(location);
            }
            return toDetail(store);
        });
    }

    @Transactional
    public StoreDetailResponse changeOperationStatus(LoginMember member, Long storeId, OperationStatusRequest request) {
        Store store = storeOwnership.getOwnedStore(member, storeId);
        store.changeOperationStatus(request.operationStatus());
        return toDetail(store);
    }

    // 7개 요일을 한 번에 교체한다
    @Transactional
    public StoreDetailResponse replaceBusinessHours(LoginMember member, Long storeId, BusinessHoursRequest request) {
        Store store = storeOwnership.getOwnedStore(member, storeId);
        Map<Short, BusinessHour> hours = businessHourRepository.findAllByStoreIdOrderByDayOfWeek(storeId).stream()
                .collect(Collectors.toMap(BusinessHour::getDayOfWeek, Function.identity()));
        for (BusinessHoursRequest.BusinessHourRequest row : request.businessHours()) {
            hours.get(row.dayOfWeek().shortValue())
                    .change(row.openTime(), row.closeTime(), row.isClosed());
        }
        return toDetail(store);
    }

    // 폐업 (soft delete). 진행 중 주문(결제완료 · 조리중 · 배달중)이 있으면 409, order 가 응답하지 않으면 503 (폐업 안 함)
    // 폐업 후에는 조회 · 관리 · 내부 API 에서 모두 404 이고, 같은 사장님이 새 가게를 다시 등록할 수 있다
    @Transactional
    public void close(LoginMember member, Long storeId) {
        Store store = storeOwnership.getOwnedStore(member, storeId);
        if (orderClient.countActiveOrders(storeId) > 0) {
            throw new BusinessException(StoreErrorCode.STORE_HAS_ACTIVE_ORDERS);
        }
        store.close(OffsetDateTime.now(clock));
    }

    private StoreDetailResponse toDetail(Store store) {
        List<BusinessHour> hours = businessHourRepository.findAllByStoreIdOrderByDayOfWeek(store.getId());
        return StoreDetailResponse.of(store, hours, LocalDateTime.now(clock), imageProperties);
    }
}
