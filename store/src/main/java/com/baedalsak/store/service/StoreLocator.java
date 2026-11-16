package com.baedalsak.store.service;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;
import com.baedalsak.store.client.GeocodeResult;
import com.baedalsak.store.client.KakaoLocalClient;
import com.baedalsak.store.domain.GeoPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 가게 주소 → 좌표 (#210). 가게 등록 · 수정에서 트랜잭션 밖에서 부른다 (카카오 대기 동안 DB 커넥션을 잡지 않음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreLocator {

    private final KakaoLocalClient kakaoLocalClient;

    /**
     * @param address 사장님이 입력한 주소
     * @return 좌표. 카카오를 확인하지 못했으면(키 없음 · 장애) null — 가게 등록이 외부 장애에 막히지 않게 좌표 없이 저장한다
     * @throws BusinessException 카카오에 그 주소가 없으면 400 VALIDATION_FAILED (field address)
     */
    public GeoPoint locate(String address) {
        return switch (kakaoLocalClient.geocode(address)) {
            case GeocodeResult.Found found -> found.location();
            case GeocodeResult.NotFound notFound -> throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                    List.of(new FieldErrorDetail("address", "주소를 찾을 수 없습니다. 도로명 주소를 다시 확인해 주세요.")));
            case GeocodeResult.Unavailable unavailable -> {
                log.warn("store address not geocoded, saved without location");
                yield null;
            }
        };
    }
}
