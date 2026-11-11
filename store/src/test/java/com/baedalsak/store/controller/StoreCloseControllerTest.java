package com.baedalsak.store.controller;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.store.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가게 폐업 (soft delete, #176). 진행 중 주문 확인은 order [내부] API (OrderClient mock)
 */
class StoreCloseControllerTest extends IntegrationTest {

    static final long OWNER_ID = 10L;

    @Test
    void 폐업하면_204_이고_조회_관리_내부_API_에서_모두_사라진다() throws Exception {
        createStore(OWNER_ID, "싹분식");
        createStore(20L, "싹치킨");
        createMenu(OWNER_ID, 1L);

        mockMvc.perform(owner(delete("/v1/stores/1"), OWNER_ID))
                .andExpect(status().isNoContent());

        // 행은 남고 폐업 시각만 기록 (기준 시각 2026-10-22 12:00 KST)
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM store.store WHERE id = 1",
                Boolean.class)).isTrue();

        // 목록 · 통계에서 빠짐
        mockMvc.perform(get("/v1/stores"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("싹치킨"));
        mockMvc.perform(get("/v1/stores/summary"))
                .andExpect(jsonPath("$.totalCount").value(1))
                // 두 가게 모두 분식 → 폐업한 1곳이 빠져 1
                .andExpect(jsonPath("$.categoryCounts.SNACK").value(1));
        // 상세 · 메뉴 · 내 가게 404
        mockMvc.perform(get("/v1/stores/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        mockMvc.perform(get("/v1/stores/1/menus"))
                .andExpect(status().isNotFound());
        mockMvc.perform(owner(get("/v1/stores/me"), OWNER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        // 관리 404
        mockMvc.perform(owner(post("/v1/stores/1/menus"), OWNER_ID).content(menuBody()))
                .andExpect(status().isNotFound());
        // 내부 API 404 → order 가 장바구니 · 주문 · 결제 승인을 STORE_NOT_FOUND 로 막는다
        mockMvc.perform(get("/internal/stores/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/internal/stores/1/menus").param("ids", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 진행_중_주문이_있으면_409_이고_폐업하지_않는다() throws Exception {
        createStore(OWNER_ID, "싹분식");
        given(orderClient.countActiveOrders(1L)).willReturn(2L);

        mockMvc.perform(owner(delete("/v1/stores/1"), OWNER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_HAS_ACTIVE_ORDERS"));

        mockMvc.perform(get("/v1/stores/1"))
                .andExpect(status().isOk());
    }

    @Test
    void order_가_응답하지_않으면_503_이고_폐업하지_않는다() throws Exception {
        createStore(OWNER_ID, "싹분식");
        given(orderClient.countActiveOrders(1L))
                .willThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE));

        mockMvc.perform(owner(delete("/v1/stores/1"), OWNER_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

        mockMvc.perform(get("/v1/stores/1"))
                .andExpect(status().isOk());
    }

    @Test
    void 다른_사장님의_가게면_403() throws Exception {
        createStore(OWNER_ID, "싹분식");

        mockMvc.perform(owner(delete("/v1/stores/1"), 20L))
                .andExpect(status().isForbidden());
    }

    @Test
    void 사장님이_아니면_403() throws Exception {
        createStore(OWNER_ID, "싹분식");

        mockMvc.perform(member(delete("/v1/stores/1"), OWNER_ID, "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 없거나_이미_폐업한_가게면_404() throws Exception {
        createStore(OWNER_ID, "싹분식");
        mockMvc.perform(owner(delete("/v1/stores/1"), OWNER_ID))
                .andExpect(status().isNoContent());

        mockMvc.perform(owner(delete("/v1/stores/1"), OWNER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        mockMvc.perform(owner(delete("/v1/stores/999"), OWNER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void 폐업한_사장님은_새_가게를_다시_등록할_수_있다() throws Exception {
        createStore(OWNER_ID, "싹분식");
        mockMvc.perform(owner(delete("/v1/stores/1"), OWNER_ID))
                .andExpect(status().isNoContent());

        createStore(OWNER_ID, "싹분식 2호점");

        mockMvc.perform(owner(get("/v1/stores/me"), OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("싹분식 2호점"));
        // 운영 중인 가게가 있으면 여전히 1개만
        mockMvc.perform(owner(post("/v1/stores"), OWNER_ID).content(storeBody("싹분식 3호점")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_ALREADY_EXISTS"));
    }

    private void createStore(long ownerId, String name) throws Exception {
        mockMvc.perform(owner(post("/v1/stores"), ownerId).content(storeBody(name)))
                .andExpect(status().isCreated());
    }

    private void createMenu(long ownerId, long storeId) throws Exception {
        mockMvc.perform(owner(post("/v1/stores/" + storeId + "/menus"), ownerId).content(menuBody()))
                .andExpect(status().isCreated());
    }

    private String storeBody(String name) {
        return """
                {"name":"%s","category":"SNACK","address":"서울시 마포구 1","minOrderAmount":12000,"deliveryFee":3000}
                """.formatted(name);
    }

    private String menuBody() {
        return """
                {"name":"참치김밥","description":"참치 듬뿍","price":4500}
                """;
    }

    private MockHttpServletRequestBuilder owner(MockHttpServletRequestBuilder request, long ownerId) {
        return member(request, ownerId, "OWNER");
    }

    private MockHttpServletRequestBuilder member(MockHttpServletRequestBuilder request, long memberId, String role) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header(LoginMember.ID_HEADER, String.valueOf(memberId))
                .header(LoginMember.ROLE_HEADER, role);
    }
}
