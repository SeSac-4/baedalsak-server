package com.baedalsak.store.repository;

import com.baedalsak.store.domain.BusinessHour;
import com.baedalsak.store.domain.BusinessHourId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BusinessHourRepository extends JpaRepository<BusinessHour, BusinessHourId> {

    List<BusinessHour> findAllByStoreIdOrderByDayOfWeek(Long storeId);

    // 가게 목록의 영업 여부 계산용 (오늘 요일만)
    List<BusinessHour> findAllByStoreIdInAndDayOfWeek(Collection<Long> storeIds, Short dayOfWeek);

    void deleteAllByStoreId(Long storeId);
}
