package com.baedalsak.store.repository;

import com.baedalsak.store.domain.Category;
import com.baedalsak.store.domain.Store;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);

    List<Store> findAllByCategory(Category category, Sort sort);
}
