package com.baedalsak.store.dto.response;

import com.baedalsak.store.config.ImageProperties;
import com.baedalsak.store.domain.Menu;
import com.baedalsak.store.domain.SaleStatus;

/**
 * 메뉴 (Menu). 설명이 없으면 description 은 null, 사진이 없으면 imageKey·imageUrl 은 null
 */
public record MenuResponse(
        Long id,
        Long storeId,
        String name,
        String description,
        Integer price,
        SaleStatus saleStatus,
        String imageKey,
        String imageUrl
) {

    public static MenuResponse from(Menu menu, ImageProperties images) {
        return new MenuResponse(menu.getId(), menu.getStoreId(), menu.getName(), menu.getDescription(),
                menu.getPrice(), menu.getSaleStatus(), menu.getImageKey(), images.urlOf(menu.getImageKey()));
    }
}
