package com.baedalsak.store.controller;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.store.support.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].name").value("싹분식"))
                    .andExpect(jsonPath("$.content[0].isOpenNow").value(true))
                    .andExpect(jsonPath("$.content[0].todayHour.dayOfWeek").value(4))
                    .andExpect(jsonPath("$.content[0].todayHour.openTime").value("09:00"))
                    .andExpect(jsonPath("$.content[1].name").value("싹치킨"))
                    .andExpect(jsonPath("$.content[1].operationStatus").value("PAUSED"))
                    .andExpect(jsonPath("$.content[1].isOpenNow").value(false))
                    // 목록에는 주소·요일별 영업시간이 없다
                    .andExpect(jsonPath("$.content[0].address").doesNotExist())
                    .andExpect(jsonPath("$.content[0].businessHours").doesNotExist())
                    // 다음 페이지 없음
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }

        @Test
        void 카테고리로_거른다() throws Exception {
            createStore(10L, "싹분식", "SNACK");
            createStore(20L, "싹치킨", "CHICKEN");

            mockMvc.perform(get("/v1/stores").param("category", "CHICKEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("싹치킨"));
        }

        @Test
        void 해당_카테고리_가게가_없으면_빈_목록() throws Exception {
            createStore(10L, "싹분식", "SNACK");

            mockMvc.perform(get("/v1/stores").param("category", "PIZZA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        void 없는_카테고리면_400() throws Exception {
            mockMvc.perform(get("/v1/stores").param("category", "BURGER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void size_만큼_나눠_주고_nextCursor_로_다음_페이지를_이어서_준다() throws Exception {
            for (long owner = 1; owner <= 5; owner++) {
                createStore(owner, "가게" + owner, "SNACK");
            }

            mockMvc.perform(get("/v1/stores").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[1].id").value(2))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").value("2"));

            mockMvc.perform(get("/v1/stores").param("size", "2").param("cursor", "2"))
                    .andExpect(jsonPath("$.content[0].id").value(3))
                    .andExpect(jsonPath("$.content[1].id").value(4))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").value("4"));

            // 마지막 페이지: 남은 1개, 다음 없음
            mockMvc.perform(get("/v1/stores").param("size", "2").param("cursor", "4"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(5))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }

        @Test
        void 남은_가게가_size_와_딱_맞으면_다음_페이지가_없다() throws Exception {
            createStore(1L, "가게1", "SNACK");
            createStore(2L, "가게2", "SNACK");

            mockMvc.perform(get("/v1/stores").param("size", "2"))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }

        @Test
        void 카테고리로_거르면서_나눈다() throws Exception {
            // id 1 분식, 2 치킨, 3 분식, 4 치킨, 5 분식
            for (long owner = 1; owner <= 5; owner++) {
                createStore(owner, "가게" + owner, owner % 2 == 1 ? "SNACK" : "CHICKEN");
            }

            mockMvc.perform(get("/v1/stores").param("category", "SNACK").param("size", "2"))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[1].id").value(3))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").value("3"));

            mockMvc.perform(get("/v1/stores").param("category", "SNACK").param("size", "2").param("cursor", "3"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(5))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void 마지막_가게_뒤의_cursor_면_빈_목록() throws Exception {
            createStore(1L, "가게1", "SNACK");

            mockMvc.perform(get("/v1/stores").param("cursor", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void size_를_안_주면_20개씩() throws Exception {
            for (long owner = 1; owner <= 21; owner++) {
                createStore(owner, "가게" + owner, "SNACK");
            }

            mockMvc.perform(get("/v1/stores"))
                    .andExpect(jsonPath("$.content.length()").value(20))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").value("20"));
        }

        @ParameterizedTest
        @CsvSource({"size, 0", "size, 101", "size, abc", "cursor, 0", "cursor, -1", "cursor, abc"})
        void 잘못된_size_나_cursor_면_400(String name, String value) throws Exception {
            mockMvc.perform(get("/v1/stores").param(name, value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    class 가게_이름_검색 {

        @Test
        void 이름에_검색어가_들어간_가게만_준다() throws Exception {
            createStore(1L, "신당동떡볶이", "SNACK");
            createStore(2L, "바삭치킨", "CHICKEN");
            createStore(3L, "국물떡볶이", "SNACK");

            mockMvc.perform(get("/v1/stores").param("q", "떡볶이"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].name").value("신당동떡볶이"))
                    .andExpect(jsonPath("$.content[1].name").value("국물떡볶이"))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void 대소문자를_구분하지_않는다() throws Exception {
            createStore(1L, "Pizza School", "PIZZA");

            mockMvc.perform(get("/v1/stores").param("q", "pizza"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Pizza School"));
        }

        @Test
        void 맞는_가게가_없으면_빈_목록() throws Exception {
            createStore(1L, "바삭치킨", "CHICKEN");

            mockMvc.perform(get("/v1/stores").param("q", "피자"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void 카테고리와_함께_거른다() throws Exception {
            createStore(1L, "치킨떡볶이", "SNACK");
            createStore(2L, "바삭치킨", "CHICKEN");

            mockMvc.perform(get("/v1/stores").param("q", "치킨").param("category", "CHICKEN"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("바삭치킨"));
        }

        @Test
        void 검색_결과도_커서로_나눈다() throws Exception {
            // id 1 떡볶이1, 2 치킨, 3 떡볶이2, 4 떡볶이3
            createStore(1L, "떡볶이1", "SNACK");
            createStore(2L, "치킨", "CHICKEN");
            createStore(3L, "떡볶이2", "SNACK");
            createStore(4L, "떡볶이3", "SNACK");

            mockMvc.perform(get("/v1/stores").param("q", "떡볶이").param("size", "2"))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[1].id").value(3))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").value("3"));

            mockMvc.perform(get("/v1/stores").param("q", "떡볶이").param("size", "2").param("cursor", "3"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(4))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void 앞뒤_공백은_지우고_공백만이면_전체() throws Exception {
            createStore(1L, "떡볶이", "SNACK");
            createStore(2L, "치킨", "CHICKEN");

            mockMvc.perform(get("/v1/stores").param("q", "  떡볶이  "))
                    .andExpect(jsonPath("$.content.length()").value(1));
            mockMvc.perform(get("/v1/stores").param("q", "   "))
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        void 퍼센트와_밑줄은_와일드카드가_아니라_글자로_찾는다() throws Exception {
            createStore(1L, "100%치킨", "CHICKEN");
            createStore(2L, "1000치킨", "CHICKEN");
            createStore(3L, "a_b분식", "SNACK");
            createStore(4L, "axb분식", "SNACK");

            mockMvc.perform(get("/v1/stores").param("q", "%"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("100%치킨"));
            mockMvc.perform(get("/v1/stores").param("q", "a_b"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("a_b분식"));
        }

        @Test
        void 검색어가_50자를_넘으면_400() throws Exception {
            mockMvc.perform(get("/v1/stores").param("q", "가".repeat(51)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            mockMvc.perform(get("/v1/stores").param("q", "가".repeat(50)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class 가게_목록_통계 {

        @Test
        void 전체_카테고리별_가게_수_영업_중_수_최저_배달비를_준다() throws Exception {
            createStore(10L, "싹분식", "SNACK", 2000);
            createStore(20L, "싹분식2", "SNACK", 0);
            createStore(30L, "싹치킨", "CHICKEN", 3000);
            // 영업 중은 운영 상태 OPEN + 오늘 영업시간 안 (기준 12:00, 기본 09:00~22:00) → 1, 3번 가게
            open(10L, 1L);
            open(30L, 3L);

            mockMvc.perform(get("/v1/stores/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(3))
                    .andExpect(jsonPath("$.openCount").value(2))
                    .andExpect(jsonPath("$.minDeliveryFee").value(0))
                    .andExpect(jsonPath("$.categoryCounts.SNACK").value(2))
                    .andExpect(jsonPath("$.categoryCounts.CHICKEN").value(1))
                    // 가게가 없는 카테고리도 0 으로 6개 모두
                    .andExpect(jsonPath("$.categoryCounts.length()").value(6))
                    .andExpect(jsonPath("$.categoryCounts.KOREAN").value(0))
                    .andExpect(jsonPath("$.categoryCounts.CAFE_DESSERT").value(0));
        }

        @Test
        void 가게가_없으면_0_이고_최저_배달비는_null() throws Exception {
            mockMvc.perform(get("/v1/stores/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0))
                    .andExpect(jsonPath("$.openCount").value(0))
                    .andExpect(jsonPath("$.minDeliveryFee").isEmpty())
                    .andExpect(jsonPath("$.categoryCounts.length()").value(6))
                    .andExpect(jsonPath("$.categoryCounts.PIZZA").value(0));
        }
    }

    // 기준 위치 북위 37.5 · 동경 127.0. 위도 0.0089932도 ≈ 1000m, 동쪽 경도 0.0334403도 ≈ 2950m (북위 37.5도)
    @Nested
    class 주변_가게_반경_조회 {

        static final String LAT = "37.5";
        static final String LNG = "127.0";

        @Test
        void 반경_안_가게만_거리와_함께_준다() throws Exception {
            createStore(1L, "1km", "SNACK");
            createStore(2L, "북쪽2950m", "SNACK");
            createStore(3L, "북쪽3050m", "SNACK");
            createStore(4L, "동쪽2950m", "SNACK");
            locate(1L, 37.5 + 0.0089932, 127.0);
            locate(2L, 37.5 + 0.0265300, 127.0);
            locate(3L, 37.5 + 0.0274293, 127.0);
            locate(4L, 37.5, 127.0 + 0.0334403);

            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.content[0].name").value("1km"))
                    .andExpect(jsonPath("$.content[0].distanceM").value(1000))
                    .andExpect(jsonPath("$.content[1].name").value("북쪽2950m"))
                    .andExpect(jsonPath("$.content[1].distanceM").value(2950))
                    // 경도 범위가 위도에 맞게 넓어져야 동쪽 2950m 가 들어온다
                    .andExpect(jsonPath("$.content[2].name").value("동쪽2950m"))
                    .andExpect(jsonPath("$.content[2].distanceM").value(2950));
        }

        @Test
        void radius_로_반경을_바꾼다() throws Exception {
            createStore(1L, "1km", "SNACK");
            createStore(2L, "북쪽3050m", "SNACK");
            locate(1L, 37.5 + 0.0089932, 127.0);
            locate(2L, 37.5 + 0.0274293, 127.0);

            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG).param("radius", "500"))
                    .andExpect(jsonPath("$.content.length()").value(0));
            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG).param("radius", "10000"))
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        void 좌표가_없는_가게는_빠진다() throws Exception {
            createStore(1L, "좌표있음", "SNACK");
            createStore(2L, "좌표없음", "SNACK");
            locate(1L, 37.5, 127.0);

            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("좌표있음"))
                    .andExpect(jsonPath("$.content[0].distanceM").value(0));
        }

        @Test
        void 위치를_안_보내면_좌표와_무관하게_전체이고_거리는_null() throws Exception {
            createStore(1L, "멀리", "SNACK");
            createStore(2L, "좌표없음", "SNACK");
            locate(1L, 35.1, 129.0);

            mockMvc.perform(get("/v1/stores"))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].distanceM").isEmpty())
                    .andExpect(jsonPath("$.content[1].distanceM").isEmpty());
        }

        @Test
        void 폐업한_가게는_빠진다() throws Exception {
            createStore(1L, "운영", "SNACK");
            createStore(2L, "폐업", "SNACK");
            locate(1L, 37.5, 127.0);
            locate(2L, 37.5, 127.0);
            jdbcTemplate.update("UPDATE store.store SET deleted_at = now() WHERE id = 2");

            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("운영"));
        }

        @Test
        void 카테고리_검색어와_함께_거른다() throws Exception {
            createStore(1L, "치킨떡볶이", "SNACK");
            createStore(2L, "바삭치킨", "CHICKEN");
            createStore(3L, "먼바삭치킨", "CHICKEN");
            locate(1L, 37.5, 127.0);
            locate(2L, 37.5, 127.0);
            locate(3L, 37.5 + 0.0449661, 127.0);

            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG)
                            .param("category", "CHICKEN").param("q", "치킨"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("바삭치킨"));
        }

        @Test
        void 검색어의_퍼센트와_밑줄_느낌표는_글자로_찾는다() throws Exception {
            createStore(1L, "100%치킨", "CHICKEN");
            createStore(2L, "1000치킨", "CHICKEN");
            createStore(3L, "a_b분식", "SNACK");
            createStore(4L, "axb분식", "SNACK");
            createStore(5L, "대박!분식", "SNACK");
            for (long id = 1; id <= 5; id++) {
                locate(id, 37.5, 127.0);
            }

            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG).param("q", "%"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("100%치킨"));
            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG).param("q", "a_b"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("a_b분식"));
            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG).param("q", "!"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("대박!분식"));
        }

        @Test
        void 반경_결과도_커서로_나눈다() throws Exception {
            // id 1 · 3 · 4 만 반경 안
            for (long owner = 1; owner <= 4; owner++) {
                createStore(owner, "가게" + owner, "SNACK");
            }
            locate(1L, 37.5, 127.0);
            locate(2L, 37.5 + 0.0449661, 127.0);
            locate(3L, 37.5, 127.0);
            locate(4L, 37.5, 127.0);

            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG).param("size", "2"))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[1].id").value(3))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").value("3"));

            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG).param("size", "2")
                            .param("cursor", "3"))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(4))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void 반경_밖_가게가_많아도_한_페이지를_채운다() throws Exception {
            // id 1~3 반경 밖, 4~6 반경 안 → size 2 첫 페이지는 4, 5 (조회 뒤 거르면 빈 페이지가 된다)
            for (long owner = 1; owner <= 6; owner++) {
                createStore(owner, "가게" + owner, "SNACK");
                locate(owner, owner <= 3 ? 35.1 : 37.5, owner <= 3 ? 129.0 : 127.0);
            }

            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG).param("size", "2"))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(4))
                    .andExpect(jsonPath("$.content[1].id").value(5))
                    .andExpect(jsonPath("$.hasNext").value(true));
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "lat=37.5|lng",
                "lng=127.0|lat",
                "radius=3000|radius",
                "lat=90.1&lng=127.0|lat",
                "lat=-90.1&lng=127.0|lat",
                "lat=37.5&lng=180.1|lng",
                "lat=NaN&lng=127.0|lat",
                "lat=37.5&lng=Infinity|lng",
                "lat=37.5&lng=127.0&radius=0|radius",
                "lat=37.5&lng=127.0&radius=10001|radius",
        })
        void 위치_파라미터가_잘못되면_400(String query, String field) throws Exception {
            mockMvc.perform(get("/v1/stores?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value(field));
            mockMvc.perform(get("/v1/stores/summary?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void 숫자가_아니면_400() throws Exception {
            mockMvc.perform(get("/v1/stores").param("lat", "abc").param("lng", LNG))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void 반경_경계값_1m_와_10000m_는_허용한다() throws Exception {
            mockMvc.perform(get("/v1/stores").param("lat", LAT).param("lng", LNG).param("radius", "1"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/v1/stores").param("lat", "90").param("lng", "-180").param("radius", "10000"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class 주변_가게_통계 {

        @Test
        void 목록과_같은_반경_안_가게_기준으로_센다() throws Exception {
            createStore(10L, "가까운분식", "SNACK", 2000);
            createStore(20L, "가까운치킨", "CHICKEN", 3000);
            createStore(30L, "먼분식", "SNACK", 0);
            createStore(40L, "좌표없는분식", "SNACK", 0);
            locate(1L, 37.5, 127.0);
            locate(2L, 37.5 + 0.0089932, 127.0);
            locate(3L, 37.5 + 0.0449661, 127.0);
            // 영업 중: 1번(반경 안) · 3번(반경 밖) → 반경 기준 1
            open(10L, 1L);
            open(30L, 3L);

            mockMvc.perform(get("/v1/stores/summary").param("lat", "37.5").param("lng", "127.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(2))
                    .andExpect(jsonPath("$.openCount").value(1))
                    .andExpect(jsonPath("$.minDeliveryFee").value(2000))
                    .andExpect(jsonPath("$.categoryCounts.SNACK").value(1))
                    .andExpect(jsonPath("$.categoryCounts.CHICKEN").value(1))
                    .andExpect(jsonPath("$.categoryCounts.length()").value(6));

            // 위치가 없으면 지금처럼 전체 기준
            mockMvc.perform(get("/v1/stores/summary"))
                    .andExpect(jsonPath("$.totalCount").value(4))
                    .andExpect(jsonPath("$.openCount").value(2))
                    .andExpect(jsonPath("$.minDeliveryFee").value(0));
        }

        @Test
        void 반경_안에_가게가_없으면_0_이고_최저_배달비는_null() throws Exception {
            createStore(10L, "먼분식", "SNACK");
            locate(1L, 35.1, 129.0);

            mockMvc.perform(get("/v1/stores/summary").param("lat", "37.5").param("lng", "127.0"))
                    .andExpect(jsonPath("$.totalCount").value(0))
                    .andExpect(jsonPath("$.openCount").value(0))
                    .andExpect(jsonPath("$.minDeliveryFee").isEmpty())
                    .andExpect(jsonPath("$.categoryCounts.SNACK").value(0));
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
        createStore(ownerId, name, category, 3000);
    }

    private void createStore(long ownerId, String name, String category, int deliveryFee) throws Exception {
        String body = """
                {"name":"%s","category":"%s","address":"서울시 마포구 1","minOrderAmount":12000,"deliveryFee":%d}
                """.formatted(name, category, deliveryFee);
        mockMvc.perform(owner(post("/v1/stores"), ownerId).content(body))
                .andExpect(status().isCreated());
    }

    // 가게 좌표를 직접 넣는다 (등록 때 카카오는 mock 이라 좌표 없이 저장됨)
    private void locate(long storeId, double lat, double lng) {
        jdbcTemplate.update("UPDATE store.store SET latitude = ?, longitude = ? WHERE id = ?", lat, lng, storeId);
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
