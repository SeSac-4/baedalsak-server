package com.baedalsak.store.controller;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.store.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메뉴 관리·조회 API (#12). 가게 1 = 사장님 10, 가게 2 = 사장님 20
 */
class MenuControllerTest extends IntegrationTest {

    static final long OWNER_ID = 10L;
    static final long OTHER_OWNER_ID = 20L;

    static final String MENU_BODY = """
            {"name":"참치김밥","description":"고소한 참치마요 듬뿍","price":4500}
            """;

    @BeforeEach
    void setUpStores() throws Exception {
        createStore(OWNER_ID);
        createStore(OTHER_OWNER_ID);
    }

    @Nested
    class 메뉴_등록 {

        @Test
        void 판매_중으로_등록된다() throws Exception {
            createMenu(OWNER_ID, 1L, MENU_BODY)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.storeId").value(1))
                    .andExpect(jsonPath("$.name").value("참치김밥"))
                    .andExpect(jsonPath("$.description").value("고소한 참치마요 듬뿍"))
                    .andExpect(jsonPath("$.price").value(4500))
                    .andExpect(jsonPath("$.saleStatus").value("ON_SALE"));
        }

        @Test
        void 설명은_선택이라_보내지_않으면_null() throws Exception {
            createMenu(OWNER_ID, 1L, """
                    {"name":"치즈김밥","price":5000}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.description").value(nullValue()));
        }

        @Test
        void 입력값이_잘못되면_400() throws Exception {
            createMenu(OWNER_ID, 1L, """
                    {"name":"","price":0}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                    .andExpect(jsonPath("$.errors[*].field", hasItem("price")));
        }

        @Test
        void 남의_가게면_403() throws Exception {
            createMenu(OTHER_OWNER_ID, 1L, MENU_BODY)
                    .andExpect(status().isForbidden());
        }

        @Test
        void 없는_가게면_404() throws Exception {
            createMenu(OWNER_ID, 999L, MENU_BODY)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }

        @Test
        void 사장님이_아니면_403() throws Exception {
            mockMvc.perform(withMember(post("/v1/stores/1/menus"), OWNER_ID, "CUSTOMER").content(MENU_BODY))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class 메뉴_목록 {

        @Test
        void 삭제된_메뉴는_빼고_품절은_포함한다() throws Exception {
            createMenu(OWNER_ID, 1L, menu("참치김밥", 4500));
            createMenu(OWNER_ID, 1L, menu("라볶이", 6000));
            createMenu(OWNER_ID, 1L, menu("모둠튀김", 4000));
            changeSaleStatus(OWNER_ID, 1L, 3L, "SOLD_OUT");
            deleteMenu(OWNER_ID, 1L, 2L);

            // 인증 불필요
            mockMvc.perform(get("/v1/stores/1/menus"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("참치김밥"))
                    .andExpect(jsonPath("$[0].saleStatus").value("ON_SALE"))
                    .andExpect(jsonPath("$[1].name").value("모둠튀김"))
                    .andExpect(jsonPath("$[1].saleStatus").value("SOLD_OUT"));
        }

        @Test
        void 다른_가게_메뉴는_나오지_않는다() throws Exception {
            createMenu(OWNER_ID, 1L, menu("참치김밥", 4500));
            createMenu(OTHER_OWNER_ID, 2L, menu("후라이드", 18000));

            mockMvc.perform(get("/v1/stores/2/menus"))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("후라이드"));
        }

        @Test
        void 메뉴가_없으면_빈_목록() throws Exception {
            mockMvc.perform(get("/v1/stores/1/menus"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void 없는_가게면_404() throws Exception {
            mockMvc.perform(get("/v1/stores/999/menus"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }
    }

    @Nested
    class 메뉴_수정 {

        @Test
        void 내_가게_메뉴를_수정한다() throws Exception {
            createMenu(OWNER_ID, 1L, MENU_BODY);

            mockMvc.perform(owner(put("/v1/stores/1/menus/1"), OWNER_ID).content("""
                            {"name":"참치마요김밥","description":null,"price":5000}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("참치마요김밥"))
                    .andExpect(jsonPath("$.description").value(nullValue()))
                    .andExpect(jsonPath("$.price").value(5000))
                    .andExpect(jsonPath("$.saleStatus").value("ON_SALE"));
        }

        @Test
        void 다른_가게의_메뉴_id면_404() throws Exception {
            createMenu(OTHER_OWNER_ID, 2L, menu("후라이드", 18000));

            // 내 가게(1) 경로로 가게 2의 메뉴(1)를 수정
            mockMvc.perform(owner(put("/v1/stores/1/menus/1"), OWNER_ID).content(MENU_BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
        }

        @Test
        void 남의_가게면_403() throws Exception {
            createMenu(OWNER_ID, 1L, MENU_BODY);

            mockMvc.perform(owner(put("/v1/stores/1/menus/1"), OTHER_OWNER_ID).content(MENU_BODY))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class 메뉴_삭제 {

        @Test
        void 삭제하면_204이고_다시_수정할_수_없다() throws Exception {
            createMenu(OWNER_ID, 1L, MENU_BODY);

            deleteMenu(OWNER_ID, 1L, 1L)
                    .andExpect(status().isNoContent());

            // 삭제된 메뉴는 수정·삭제·상태 변경 대상이 아니다
            deleteMenu(OWNER_ID, 1L, 1L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
            changeSaleStatus(OWNER_ID, 1L, 1L, "SOLD_OUT")
                    .andExpect(status().isNotFound());
        }

        @Test
        void soft_delete라_행은_남는다() throws Exception {
            createMenu(OWNER_ID, 1L, MENU_BODY);
            deleteMenu(OWNER_ID, 1L, 1L);

            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM store.menu WHERE id = 1 AND deleted_at IS NOT NULL", Integer.class);
            assertThat(rows).isEqualTo(1);
        }

        @Test
        void 남의_가게면_403() throws Exception {
            createMenu(OWNER_ID, 1L, MENU_BODY);

            deleteMenu(OTHER_OWNER_ID, 1L, 1L)
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class 판매_상태 {

        @Test
        void 품절과_판매_중을_전환한다() throws Exception {
            createMenu(OWNER_ID, 1L, MENU_BODY);

            changeSaleStatus(OWNER_ID, 1L, 1L, "SOLD_OUT")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saleStatus").value("SOLD_OUT"));
            changeSaleStatus(OWNER_ID, 1L, 1L, "ON_SALE")
                    .andExpect(jsonPath("$.saleStatus").value("ON_SALE"));
        }

        @Test
        void 없는_상태값이면_400() throws Exception {
            createMenu(OWNER_ID, 1L, MENU_BODY);

            changeSaleStatus(OWNER_ID, 1L, 1L, "HIDDEN")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 남의_가게면_403() throws Exception {
            createMenu(OWNER_ID, 1L, MENU_BODY);

            changeSaleStatus(OTHER_OWNER_ID, 1L, 1L, "SOLD_OUT")
                    .andExpect(status().isForbidden());
        }
    }

    private void createStore(long ownerId) throws Exception {
        mockMvc.perform(owner(post("/v1/stores"), ownerId).content("""
                        {"name":"싹분식","category":"SNACK","address":"서울시 마포구 1","minOrderAmount":12000,"deliveryFee":3000}
                        """))
                .andExpect(status().isCreated());
    }

    private String menu(String name, int price) {
        return "{\"name\":\"" + name + "\",\"price\":" + price + "}";
    }

    private ResultActions createMenu(long ownerId, long storeId, String body) throws Exception {
        return mockMvc.perform(owner(post("/v1/stores/" + storeId + "/menus"), ownerId).content(body));
    }

    private ResultActions deleteMenu(long ownerId, long storeId, long menuId) throws Exception {
        return mockMvc.perform(owner(delete("/v1/stores/" + storeId + "/menus/" + menuId), ownerId));
    }

    private ResultActions changeSaleStatus(long ownerId, long storeId, long menuId, String saleStatus) throws Exception {
        return mockMvc.perform(owner(patch("/v1/stores/" + storeId + "/menus/" + menuId + "/status"), ownerId)
                .content("{\"saleStatus\":\"" + saleStatus + "\"}"));
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
