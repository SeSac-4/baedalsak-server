package com.baedalsak.delivery.service;

import com.baedalsak.delivery.dto.RiderDeliveryHistoryResponse;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 라이더 배달 내역. 기간 안에 완료한 배달을 돌려준다.
 * 날짜는 한국 시간 기준이라, 자정 근처에 완료한 배달도 한국 날짜로 묶인다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryHistoryService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MAX_DAYS = 93;

    private final DeliveryRepository deliveryRepository;

    // from ~ to 는 둘 다 포함. 한 번에 최대 93일(약 3달)까지
    @Transactional(readOnly = true)
    public List<RiderDeliveryHistoryResponse> getHistory(Long riderId, LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간은 " + MAX_DAYS + "일 이내로 선택해 주세요.");
        }
        return deliveryRepository
                .findCompletedByRider(riderId,
                        from.atStartOfDay(KST).toOffsetDateTime(),
                        to.plusDays(1).atStartOfDay(KST).toOffsetDateTime())
                .stream()
                .map(RiderDeliveryHistoryResponse::from)
                .toList();
    }
}
