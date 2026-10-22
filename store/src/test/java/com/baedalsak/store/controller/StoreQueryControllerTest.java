package com.baedalsak.store.controller;

import com.baedalsak.store.common.auth.LoginMember;
import com.baedalsak.store.support.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카테고리·가게 조회 API (#10). 기준 시각 2026-10-22(목) 12:00 KST, 목요일 = dayOfWeek 4
 */
class StoreQueryControllerTest extends IntegrationTest {

    @Test
    void 카테고리_6개를_코드와_이름으로_조회한다() throws Exception {
        mockMvc.perform(get("/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].code").value("KOREAN"))
                .andExpect(jsonPath("$[0].name").value("한식"))
                .andExpect(jsonPath("$[5].code").value("CAFE_DESSERT"))
                .andExpect(jsonPath("$[5].name").value("카페·디저트"));
    }

    @Nested
    class 가게_목록 {

        @Test
        void 카테고리가_없으면_전체_가게를_등록순으로_조회한다() throws Exception {
            createStore(10L, "싹분식", "SNACK");
            createStore(20L, "싹치킨", "CHICKEN");
            open(10L, 1L);

            mockMvc.perform(get("/v1/stores"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("싹분식"))
                    .andExpect(jsonPath("$[0].isOpenNow").value(true))
                    .andExpect(jsonPath("$[0].todayHour.dayOfWeek").value(4))
                    .andExpect(jsonPath("$[0].todayHour.openTime").value("09:00"))
                    .andExpect(jsonPath("$[1].name").value("싹치킨"))
                    .andExpect(jsonPath("$[1].operationStatus").value("PAUSED"))
                    .andExpect(jsonPath("$[1].isOpenNow").value(false))
                    // 목록에는 주소·요일별 영업시간이 없다
                    .andExpect(jsonPath("$[0].address").doesNotExist())
                    .andExpect(jsonPath("$[0].businessHours").doesNotExist());
        }

        @Test
        void 카테고리로_거른다() throws Exception {
            createStore(10L, "싹분식", "SNACK");
            createStore(20L, "싹치킨", "CHICKEN");

            mockMvc.perform(get("/v1/stores").param("category", "CHICKEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("싹치킨"));
        }

        @Test
        void 해당_카테고리_가게가_없으면_빈_목록() throws Exception {
            createStore(10L, "싹분식", "SNACK");

            mockMvc.perform(get("/v1/stores").param("category", "PIZZA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void 없는_카테고리면_400() throws Exception {
            mockMvc.perform(get("/v1/stores").param("category", "BURGER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    class 가게_상세 {

        @Test
        void 가게_정보와_오늘_요일별_영업시간을_조회한다() throws Exception {
            createStore(10L, "싹분식", "SNACK");
            open(10L, 1L);

            mockMvc.perform(get("/v1/stores/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("싹분식"))
                    .andExpect(jsonPath("$.address").value("서울시 마포구 1"))
                    .andExpect(jsonPath("$.minOrderAmount").value(12000))
                    .andExpect(jsonPath("$.deliveryFee").value(3000))
                    .andExpect(jsonPath("$.isOpenNow").value(true))
                    .andExpect(jsonPath("$.todayHour.dayOfWeek").value(4))
                    .andExpect(jsonPath("$.businessHours.length()").value(7));
        }

        @Test
        void 없는_가게면_404() throws Exception {
            mockMvc.perform(get("/v1/stores/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }
    }

    @Nested
    class 내_가게 {

        @Test
        void 사장님의_가게를_조회한다() throws Exception {
            createStore(10L, "싹분식", "SNACK");
            createStore(20L, "싹치킨", "CHICKEN");

            mockMvc.perform(owner(get("/v1/stores/me"), 20L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(2))
                    .andExpect(jsonPath("$.name").value("싹치킨"));
        }

        @Test
        void 아직_가게를_등록하지_않았으면_404() throws Exception {
            mockMvc.perform(owner(get("/v1/stores/me"), 10L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }

        @Test
        void 요청자_헤더가_없으면_401() throws Exception {
            mockMvc.perform(get("/v1/stores/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void createStore(long ownerId, String name, String category) throws Exception {
        String body = """
                {"name":"%s","category":"%s","address":"서울시 마포구 1","minOrderAmount":12000,"deliveryFee":3000}
                """.formatted(name, category);
        mockMvc.perform(owner(post("/v1/stores"), ownerId).content(body))
                .andExpect(status().isCreated());
    }

    private void open(long ownerId, long storeId) throws Exception {
        mockMvc.perform(owner(patch("/v1/stores/" + storeId + "/status"), ownerId)
                        .content("{\"operationStatus\":\"OPEN\"}"))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder owner(MockHttpServletRequestBuilder request, long ownerId) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header(LoginMember.ID_HEADER, String.valueOf(ownerId))
                .header(LoginMember.ROLE_HEADER, "OWNER");
    }
}
