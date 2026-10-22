package com.baedalsak.store.repository;

import com.baedalsak.store.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    // 삭제된 메뉴 제외, 품절은 포함 (2.2.1)
    List<Menu> findAllByStoreIdAndDeletedAtIsNullOrderById(Long storeId);

    Optional<Menu> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);

    // [내부] 메뉴 일괄 조회. 없는 id·삭제된 메뉴는 결과에서 빠진다
    List<Menu> findAllByStoreIdAndIdInAndDeletedAtIsNull(Long storeId, Collection<Long> ids);
}
