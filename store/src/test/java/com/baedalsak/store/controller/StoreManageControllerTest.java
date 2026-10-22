package com.baedalsak.store.controller;

import com.baedalsak.store.common.auth.LoginMember;
import com.baedalsak.store.support.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가게 등록·수정·운영 상태·영업시간 API (#11). 기준 시각 2026-10-22(목) 12:00 KST, 목요일 = dayOfWeek 4
 */
class StoreManageControllerTest extends IntegrationTest {

    static final long OWNER_ID = 10L;
    static final long OTHER_OWNER_ID = 20L;

    static final String STORE_BODY = """
            {"name":"싹분식","category":"SNACK","address":"서울시 마포구 1","minOrderAmount":12000,"deliveryFee":3000}
            """;

    @Nested
    class 가게_등록 {

        @Test
        void 임시_중지와_기본_영업시간_7일로_생성된다() throws Exception {
            createStore(OWNER_ID)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("싹분식"))
                    .andExpect(jsonPath("$.category").value("SNACK"))
                    .andExpect(jsonPath("$.operationStatus").value("PAUSED"))
                    .andExpect(jsonPath("$.isOpenNow").value(false))
                    .andExpect(jsonPath("$.todayHour.dayOfWeek").value(4))
                    .andExpect(jsonPath("$.businessHours.length()").value(7))
                    .andExpect(jsonPath("$.businessHours[0].dayOfWeek").value(0))
                    .andExpect(jsonPath("$.businessHours[0].openTime").value("09:00"))
                    .andExpect(jsonPath("$.businessHours[0].closeTime").value("22:00"))
                    .andExpect(jsonPath("$.businessHours[0].isClosed").value(false));
        }

        @Test
        void 사장님당_가게는_1개만_등록된다() throws Exception {
            createStore(OWNER_ID);

            createStore(OWNER_ID)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("STORE_ALREADY_EXISTS"));
        }

        @Test
        void 사장님이_아니면_403() throws Exception {
            mockMvc.perform(withMember(post("/v1/stores"), 1L, "CUSTOMER").content(STORE_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        void 요청자_헤더가_없으면_401() throws Exception {
            mockMvc.perform(post("/v1/stores").contentType(MediaType.APPLICATION_JSON).content(STORE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 입력값이_잘못되면_필드별_사유와_400() throws Exception {
            String body = """
                    {"name":" ","category":"BURGER","address":"주소","minOrderAmount":0,"deliveryFee":0}
                    """;
            mockMvc.perform(owner(post("/v1/stores"), OWNER_ID).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

            String negative = """
                    {"name":"","category":"SNACK","address":"주소","minOrderAmount":-1,"deliveryFee":0}
                    """;
            mockMvc.perform(owner(post("/v1/stores"), OWNER_ID).content(negative))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                    .andExpect(jsonPath("$.errors[*].field", hasItem("minOrderAmount")));
        }
    }

    @Nested
    class 가게_수정 {

        @Test
        void 내_가게를_수정한다() throws Exception {
            createStore(OWNER_ID);
            String body = """
                    {"name":"싹치킨","category":"CHICKEN","address":"서울시 용산구 2","minOrderAmount":15000,"deliveryFee":0}
                    """;

            mockMvc.perform(owner(put("/v1/stores/1"), OWNER_ID).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("싹치킨"))
                    .andExpect(jsonPath("$.category").value("CHICKEN"))
                    .andExpect(jsonPath("$.deliveryFee").value(0));
        }

        @Test
        void 남의_가게면_403() throws Exception {
            createStore(OWNER_ID);

            mockMvc.perform(owner(put("/v1/stores/1"), OTHER_OWNER_ID).content(STORE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 없는_가게면_404() throws Exception {
            mockMvc.perform(owner(put("/v1/stores/999"), OWNER_ID).content(STORE_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }
    }

    @Nested
    class 운영_상태 {

        @Test
        void 정상_운영으로_바꾸면_영업시간_안이라_영업_중() throws Exception {
            createStore(OWNER_ID);

            changeStatus(OWNER_ID, "OPEN")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.operationStatus").value("OPEN"))
                    .andExpect(jsonPath("$.isOpenNow").value(true));
        }

        @Test
        void 임시_중지로_바꾸면_영업_종료() throws Exception {
            createStore(OWNER_ID);
            changeStatus(OWNER_ID, "OPEN");

            changeStatus(OWNER_ID, "PAUSED")
                    .andExpect(jsonPath("$.operationStatus").value("PAUSED"))
                    .andExpect(jsonPath("$.isOpenNow").value(false));
        }

        @Test
        void 없는_상태값이면_400() throws Exception {
            createStore(OWNER_ID);

            changeStatus(OWNER_ID, "CLOSED")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void 남의_가게면_403() throws Exception {
            createStore(OWNER_ID);

            changeStatus(OTHER_OWNER_ID, "OPEN")
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class 영업시간_설정 {

        @Test
        void 일곱_요일을_한_번에_교체한다() throws Exception {
            createStore(OWNER_ID);
            changeStatus(OWNER_ID, "OPEN");

            // 목요일(4) 휴무 → 오늘 영업 종료
            replaceHours(OWNER_ID, week("""
                    {"dayOfWeek":4,"openTime":null,"closeTime":null,"isClosed":true}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isOpenNow").value(false))
                    .andExpect(jsonPath("$.todayHour.isClosed").value(true))
                    .andExpect(jsonPath("$.todayHour.openTime").value(nullValue()))
                    .andExpect(jsonPath("$.businessHours[1].openTime").value("11:00"))
                    .andExpect(jsonPath("$.businessHours[1].closeTime").value("21:30"));
        }

        @Test
        void 오늘_영업시간이_지나면_영업_종료() throws Exception {
            createStore(OWNER_ID);
            changeStatus(OWNER_ID, "OPEN");

            // 목요일 13:00~22:00 → 12:00 은 영업 전
            replaceHours(OWNER_ID, week("""
                    {"dayOfWeek":4,"openTime":"13:00","closeTime":"22:00","isClosed":false}"""))
                    .andExpect(jsonPath("$.isOpenNow").value(false));
        }

        @Test
        void 영업일인데_시간이_없으면_400() throws Exception {
            createStore(OWNER_ID);

            replaceHours(OWNER_ID, week("""
                    {"dayOfWeek":4,"openTime":null,"closeTime":"22:00","isClosed":false}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("businessHours[4].timeValid"));
        }

        @Test
        void 마감이_오픈보다_빠르면_400() throws Exception {
            createStore(OWNER_ID);

            replaceHours(OWNER_ID, week("""
                    {"dayOfWeek":4,"openTime":"22:00","closeTime":"09:00","isClosed":false}"""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 요일이_중복되면_400() throws Exception {
            createStore(OWNER_ID);

            // 목요일 자리에 금요일(5)을 한 번 더
            replaceHours(OWNER_ID, week("""
                    {"dayOfWeek":5,"openTime":"11:00","closeTime":"21:30","isClosed":false}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("everyDayOnce"));
        }

        @Test
        void 일곱_요일이_아니면_400() throws Exception {
            createStore(OWNER_ID);
            String body = """
                    {"businessHours":[{"dayOfWeek":0,"openTime":"09:00","closeTime":"22:00","isClosed":false}]}
                    """;

            replaceHours(OWNER_ID, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("businessHours"));
        }

        @Test
        void 남의_가게면_403() throws Exception {
            createStore(OWNER_ID);

            replaceHours(OTHER_OWNER_ID, week("""
                    {"dayOfWeek":4,"openTime":"09:00","closeTime":"22:00","isClosed":false}"""))
                    .andExpect(status().isForbidden());
        }

        // 0~6 요일 중 목요일(4) 자리만 바꾼 7행. 나머지는 11:00~21:30
        private String week(String thursday) {
            StringBuilder rows = new StringBuilder();
            for (int day = 0; day < 7; day++) {
                if (day > 0) {
                    rows.append(',');
                }
                rows.append(day == 4 ? thursday
                        : "{\"dayOfWeek\":" + day + ",\"openTime\":\"11:00\",\"closeTime\":\"21:30\",\"isClosed\":false}");
            }
            return "{\"businessHours\":[" + rows + "]}";
        }
    }

    private ResultActions createStore(long ownerId) throws Exception {
        return mockMvc.perform(owner(post("/v1/stores"), ownerId).content(STORE_BODY));
    }

    private ResultActions changeStatus(long ownerId, String status) throws Exception {
        return mockMvc.perform(owner(patch("/v1/stores/1/status"), ownerId)
                .content("{\"operationStatus\":\"" + status + "\"}"));
    }

    private ResultActions replaceHours(long ownerId, String body) throws Exception {
        return mockMvc.perform(owner(put("/v1/stores/1/business-hours"), ownerId).content(body));
    }

    private MockHttpServletRequestBuilder owner(MockHttpServletRequestBuilder request, long ownerId) {
        return withMember(request, ownerId, "OWNER");
    }

    private MockHttpServletRequestBuilder withMember(MockHttpServletRequestBuilder request, long memberId, String role) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header(LoginMember.ID_HEADER, String.valueOf(memberId))
                .header(LoginMember.ROLE_HEADER, role);
    }
}
