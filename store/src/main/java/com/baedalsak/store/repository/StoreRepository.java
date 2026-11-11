package com.baedalsak.store.repository;

import com.baedalsak.store.domain.Category;
import com.baedalsak.store.domain.Store;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);

    // 가게 목록 커서 조회 (#169): id 가 cursor 보다 큰 가게를 id 순으로 limit 개
    List<Store> findByIdGreaterThanOrderByIdAsc(Long cursor, Limit limit);

    List<Store> findByCategoryAndIdGreaterThanOrderByIdAsc(Category category, Long cursor, Limit limit);
}
