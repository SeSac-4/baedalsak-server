package com.baedalsak.store.controller;

import com.baedalsak.store.common.auth.LoginMember;
import com.baedalsak.store.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [내부] 가게·메뉴 조회 API (#26). 인증 헤더 없이 호출한다.
 * 기준 시각 2026-10-22(목) 12:00 KST. 가게 1 = 사장님 10, 가게 2 = 사장님 20
 */
class StoreInternalControllerTest extends IntegrationTest {

    @BeforeEach
    void setUpStores() throws Exception {
        createStore(10L, "싹분식");
        createStore(20L, "싹치킨");
    }

    @Nested
    class 가게_조회 {

        @Test
        void 주문_검증에_필요한_정보를_돌려준다() throws Exception {
            mockMvc.perform(get("/internal/stores/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("싹분식"))
                    .andExpect(jsonPath("$.address").value("서울시 마포구 1"))
                    .andExpect(jsonPath("$.ownerId").value(10))
                    .andExpect(jsonPath("$.deliveryFee").value(3000))
                    .andExpect(jsonPath("$.minOrderAmount").value(12000))
                    // 등록 직후 임시 중지
                    .andExpect(jsonPath("$.isOpenNow").value(false))
                    // 화면용 필드는 없다
                    .andExpect(jsonPath("$.category").doesNotExist())
                    .andExpect(jsonPath("$.businessHours").doesNotExist());
        }

        @Test
        void 정상_운영이고_영업시간_안이면_영업_중() throws Exception {
            open(10L, 1L);

            mockMvc.perform(get("/internal/stores/1"))
                    .andExpect(jsonPath("$.isOpenNow").value(true));
        }

        @Test
        void 오늘이_휴무면_영업_종료() throws Exception {
            open(10L, 1L);
            closeThursday(10L, 1L);

            mockMvc.perform(get("/internal/stores/1"))
                    .andExpect(jsonPath("$.isOpenNow").value(false));
        }

        @Test
        void 없는_가게면_404() throws Exception {
            mockMvc.perform(get("/internal/stores/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }
    }

    @Nested
    class 메뉴_일괄_조회 {

        @BeforeEach
        void setUpMenus() throws Exception {
            createMenu(10L, 1L, "참치김밥", 4500);   // 1
            createMenu(10L, 1L, "라볶이", 6000);     // 2
            createMenu(10L, 1L, "모둠튀김", 4000);   // 3 → 품절
            createMenu(10L, 1L, "치즈김밥", 5000);   // 4 → 삭제
            createMenu(20L, 2L, "후라이드", 18000);  // 5 (다른 가게)
            soldOut(10L, 1L, 3L);
            deleteMenu(10L, 1L, 4L);
        }

        @Test
        void 요청한_메뉴의_이름_가격_판매_상태를_돌려준다() throws Exception {
            mockMvc.perform(get("/internal/stores/1/menus").param("ids", "1,3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].name").value("참치김밥"))
                    .andExpect(jsonPath("$[0].price").value(4500))
                    .andExpect(jsonPath("$[0].saleStatus").value("ON_SALE"))
                    .andExpect(jsonPath("$[1].id").value(3))
                    .andExpect(jsonPath("$[1].saleStatus").value("SOLD_OUT"));
        }

        @Test
        void 없는_메뉴_삭제된_메뉴_다른_가게_메뉴는_빠진다() throws Exception {
            mockMvc.perform(get("/internal/stores/1/menus").param("ids", "2,4,5,999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(2));
        }

        @Test
        void ids를_여러_번_나눠_보내도_된다() throws Exception {
            mockMvc.perform(get("/internal/stores/1/menus").param("ids", "1").param("ids", "2"))
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void ids가_없거나_숫자가_아니면_400() throws Exception {
            mockMvc.perform(get("/internal/stores/1/menus"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            mockMvc.perform(get("/internal/stores/1/menus").param("ids", "a,b"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 없는_가게면_404() throws Exception {
            mockMvc.perform(get("/internal/stores/999/menus").param("ids", "1"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }
    }

    private void createStore(long ownerId, String name) throws Exception {
        mockMvc.perform(owner(post("/v1/stores"), ownerId).content("""
                        {"name":"%s","category":"SNACK","address":"서울시 마포구 1","minOrderAmount":12000,"deliveryFee":3000}
                        """.formatted(name)))
                .andExpect(status().isCreated());
    }

    private void open(long ownerId, long storeId) throws Exception {
        mockMvc.perform(owner(patch("/v1/stores/" + storeId + "/status"), ownerId)
                        .content("{\"operationStatus\":\"OPEN\"}"))
                .andExpect(status().isOk());
    }

    // 목요일(4)만 휴무, 나머지 09:00~22:00
    private void closeThursday(long ownerId, long storeId) throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int day = 0; day < 7; day++) {
            if (day > 0) {
                rows.append(',');
            }
            rows.append(day == 4
                    ? "{\"dayOfWeek\":4,\"openTime\":null,\"closeTime\":null,\"isClosed\":true}"
                    : "{\"dayOfWeek\":" + day + ",\"openTime\":\"09:00\",\"closeTime\":\"22:00\",\"isClosed\":false}");
        }
        mockMvc.perform(owner(put("/v1/stores/" + storeId + "/business-hours"), ownerId)
                        .content("{\"businessHours\":[" + rows + "]}"))
                .andExpect(status().isOk());
    }

    private void createMenu(long ownerId, long storeId, String name, int price) throws Exception {
        mockMvc.perform(owner(post("/v1/stores/" + storeId + "/menus"), ownerId)
                        .content("{\"name\":\"" + name + "\",\"price\":" + price + "}"))
                .andExpect(status().isCreated());
    }

    private void soldOut(long ownerId, long storeId, long menuId) throws Exception {
        mockMvc.perform(owner(patch("/v1/stores/" + storeId + "/menus/" + menuId + "/status"), ownerId)
                        .content("{\"saleStatus\":\"SOLD_OUT\"}"))
                .andExpect(status().isOk());
    }

    private void deleteMenu(long ownerId, long storeId, long menuId) throws Exception {
        mockMvc.perform(owner(delete("/v1/stores/" + storeId + "/menus/" + menuId), ownerId))
                .andExpect(status().isNoContent());
    }

    private MockHttpServletRequestBuilder owner(MockHttpServletRequestBuilder request, long ownerId) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header(LoginMember.ID_HEADER, String.valueOf(ownerId))
                .header(LoginMember.ROLE_HEADER, "OWNER");
    }
}
