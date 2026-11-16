package com.baedalsak.store.service;

import com.baedalsak.store.client.GeocodeResult;
import com.baedalsak.store.client.KakaoLocalClient;
import com.baedalsak.store.client.KakaoProperties;
import com.baedalsak.store.domain.GeoPoint;
import com.baedalsak.store.domain.Store;
import com.baedalsak.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 좌표가 없는 가게의 좌표를 채운다 (#210).
 * - 좌표 컬럼이 생기기 전에 등록된 가게, seed 가게 (seed SQL 에는 주소만 있다)
 * - 등록 · 수정 때 카카오 장애로 좌표 없이 저장된 가게
 * 앱이 뜬 뒤 가상 스레드에서 한 번 돈다 (기동 · readiness 를 늦추지 않음). 카카오 키가 없으면 건너뛴다.
 * 파드가 여러 개라 동시에 돌아도 fillLocation 이 좌표가 빈 행만 바꾸므로 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreLocationBackfill {

    private final StoreRepository storeRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final KakaoProperties kakaoProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!kakaoProperties.enabled()) {
            log.info("store location backfill skipped: no kakao key");
            return;
        }
        Thread.ofVirtual().name("store-location-backfill").start(this::fillMissingLocations);
    }

    /**
     * 좌표가 없는 가게를 하나씩 변환해 채운다. 실패한 가게는 건너뛰고 다음 기동 때 다시 시도한다.
     *
     * @return 채운 가게 수
     */
    public int fillMissingLocations() {
        List<Store> targets = storeRepository.findByLatitudeIsNullOrderByIdAsc();
        int filled = 0;
        int notFound = 0;
        int unavailable = 0;
        for (Store store : targets) {
            switch (kakaoLocalClient.geocode(store.getAddress())) {
                case GeocodeResult.Found found -> filled += fill(store, found.location());
                case GeocodeResult.NotFound notFoundResult -> {
                    notFound++;
                    log.warn("store location not found: storeId={}", store.getId());
                }
                case GeocodeResult.Unavailable unavailableResult -> unavailable++;
            }
        }
        log.info("store location backfill done: targets={}, filled={}, notFound={}, unavailable={}",
                targets.size(), filled, notFound, unavailable);
        return filled;
    }

    private int fill(Store store, GeoPoint location) {
        return storeRepository.fillLocation(store.getId(), store.getAddress(), location.lat(), location.lng());
    }
}
