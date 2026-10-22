package com.baedalsak.store.controller;

import com.baedalsak.store.dto.response.CategoryResponse;
import com.baedalsak.store.service.StoreQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 카테고리 API. gateway 가 /v1/categories 를 이 서비스로 보낸다.
 */
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final StoreQueryService storeQueryService;

    // 1.5.1 고정 카테고리 6개
    @GetMapping("/v1/categories")
    public List<CategoryResponse> getCategories() {
        return storeQueryService.getCategories();
    }
}
