package com.baedalsak.store.controller;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.store.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가게·메뉴 사진 (#110). 가게 1 = 사장님 10, 가게 2 = 사장님 20
 */
class ImageControllerTest extends IntegrationTest {

    static final long OWNER_ID = 10L;
    static final long OTHER_OWNER_ID = 20L;

    static final String UPLOAD_BODY = """
            {"target":"MENU","contentType":"image/png","size":123456}
            """;
    static final String MY_KEY = "stores/1/3f2a9c1e-0000-4000-8000-000000000001.jpg";
    static final String OTHER_STORE_KEY = "stores/2/3f2a9c1e-0000-4000-8000-000000000002.jpg";

    @BeforeEach
    void setUpStores() throws Exception {
        createStore(OWNER_ID);
        createStore(OTHER_OWNER_ID);
    }

    @Nested
    class 업로드_URL_발급 {

        @Test
        void 가게_키와_서명된_PUT_URL을_준다() throws Exception {
            String body = issue(OWNER_ID, 1L, UPLOAD_BODY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageKey", matchesPattern("stores/1/[0-9a-f-]{36}\\.png")))
                    .andExpect(jsonPath("$.expiresAt", endsWith("+09:00")))
                    .andReturn().getResponse().getContentAsString();

            String imageKey = JsonPath.read(body, "$.imageKey");
            String uploadUrl = JsonPath.read(body, "$.uploadUrl");
            // S3 객체 키 = imageKey (CloudFront 가 /images 를 떼고 S3 로 보내므로 앞에 붙이지 않는다)
            assertThat(uploadUrl).startsWith("https://baedalsak-images.s3.ap-northeast-2.amazonaws.com/" + imageKey + "?");
            // 형식·크기를 S3 에서도 강제하도록 Content-Type·Content-Length 가 서명에 들어간다
            assertThat(uploadUrl).contains("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost");
            assertThat(uploadUrl).contains("X-Amz-Expires=300");
            // 프론트는 Content-Type 헤더 하나만 보내므로 체크섬 헤더를 요구하면 안 된다
            assertThat(uploadUrl).doesNotContainIgnoringCase("checksum");
        }

        @Test
        void 확장자는_형식으로_정한다() throws Exception {
            issue(OWNER_ID, 1L, """
                    {"target":"STORE","contentType":"image/jpeg","size":1}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageKey", endsWith(".jpg")));
            issue(OWNER_ID, 1L, """
                    {"target":"STORE","contentType":"image/webp","size":5242880}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageKey", endsWith(".webp")));
        }

        @Test
        void 지원하지_않는_형식이면_400() throws Exception {
            issue(OWNER_ID, 1L, """
                    {"target":"MENU","contentType":"image/gif","size":100}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("contentType"))
                    .andExpect(jsonPath("$.errors[0].reason").value("JPG, PNG, WEBP 사진만 올릴 수 있습니다."));
        }

        @Test
        void 빈_파일이면_400() throws Exception {
            issue(OWNER_ID, 1L, """
                    {"target":"MENU","contentType":"image/png","size":0}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("size"))
                    .andExpect(jsonPath("$.errors[0].reason").value("빈 파일은 올릴 수 없습니다."));
        }

        @Test
        void 크기가_5MB를_넘으면_400() throws Exception {
            issue(OWNER_ID, 1L, """
                    {"target":"MENU","contentType":"image/png","size":5242881}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("size"))
                    .andExpect(jsonPath("$.errors[0].reason").value("사진은 5MB 이하만 올릴 수 있습니다."));
        }

        @Test
        void 남의_가게면_403() throws Exception {
            issue(OTHER_OWNER_ID, 1L, UPLOAD_BODY)
                    .andExpect(status().isForbidden());
        }

        @Test
        void 사장님이_아니면_403() throws Exception {
            mockMvc.perform(withMember(post("/v1/stores/1/images/upload-url"), OWNER_ID, "CUSTOMER")
                            .content(UPLOAD_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 없는_가게면_404() throws Exception {
            issue(OWNER_ID, 999L, UPLOAD_BODY)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        }
    }

    @Nested
    class 가게_사진 {

        @Test
        void 수정하면_키를_저장하고_URL을_붙여_응답한다() throws Exception {
            updateStore(OWNER_ID, 1L, MY_KEY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageKey").value(MY_KEY))
                    .andExpect(jsonPath("$.imageUrl").value("https://baedalsak.click/images/" + MY_KEY));

            mockMvc.perform(get("/v1/stores/1"))
                    .andExpect(jsonPath("$.imageUrl").value("https://baedalsak.click/images/" + MY_KEY));
            mockMvc.perform(get("/v1/stores"))
                    .andExpect(jsonPath("$[0].imageKey").value(MY_KEY))
                    .andExpect(jsonPath("$[0].imageUrl").value("https://baedalsak.click/images/" + MY_KEY))
                    .andExpect(jsonPath("$[1].imageUrl").value(nullValue()));
            mockMvc.perform(owner(get("/v1/stores/me"), OWNER_ID))
                    .andExpect(jsonPath("$.imageKey").value(MY_KEY));
            mockMvc.perform(owner(patch("/v1/stores/1/status"), OWNER_ID).content("{\"operationStatus\":\"OPEN\"}"))
                    .andExpect(jsonPath("$.imageKey").value(MY_KEY));
        }

        @Test
        void null로_수정하면_사진을_지운다() throws Exception {
            updateStore(OWNER_ID, 1L, MY_KEY);

            updateStore(OWNER_ID, 1L, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageKey").value(nullValue()))
                    .andExpect(jsonPath("$.imageUrl").value(nullValue()));
        }

        @Test
        void 다른_가게_키면_400() throws Exception {
            updateStore(OWNER_ID, 1L, OTHER_STORE_KEY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("imageKey"));
        }

        @Test
        void 키가_255자를_넘으면_400() throws Exception {
            updateStore(OWNER_ID, 1L, "stores/1/" + "a".repeat(247))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("imageKey"));
        }

        @Test
        void 등록할_때_보낸_키는_무시한다() throws Exception {
            mockMvc.perform(owner(post("/v1/stores"), 30L).content("""
                            {"name":"싹치킨","category":"CHICKEN","address":"서울시 용산구 2","minOrderAmount":15000,"deliveryFee":0,"imageKey":"stores/3/a.jpg"}
                            """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.imageKey").value(nullValue()))
                    .andExpect(jsonPath("$.imageUrl").value(nullValue()));
        }
    }

    @Nested
    class 메뉴_사진 {

        @Test
        void 등록과_수정에_키를_저장한다() throws Exception {
            createMenu(OWNER_ID, 1L, MY_KEY)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.imageKey").value(MY_KEY))
                    .andExpect(jsonPath("$.imageUrl").value("https://baedalsak.click/images/" + MY_KEY));

            String newKey = "stores/1/3f2a9c1e-0000-4000-8000-000000000003.webp";
            mockMvc.perform(owner(put("/v1/stores/1/menus/1"), OWNER_ID).content(menuBody(newKey)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageKey").value(newKey));

            mockMvc.perform(get("/v1/stores/1/menus"))
                    .andExpect(jsonPath("$[0].imageUrl").value("https://baedalsak.click/images/" + newKey));
            mockMvc.perform(owner(patch("/v1/stores/1/menus/1/status"), OWNER_ID).content("{\"saleStatus\":\"SOLD_OUT\"}"))
                    .andExpect(jsonPath("$.imageKey").value(newKey));
        }

        @Test
        void 사진_없이_등록하면_null() throws Exception {
            createMenu(OWNER_ID, 1L, null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.imageKey").value(nullValue()))
                    .andExpect(jsonPath("$.imageUrl").value(nullValue()));
        }

        @Test
        void 다른_가게_키면_400() throws Exception {
            createMenu(OWNER_ID, 1L, OTHER_STORE_KEY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("imageKey"));

            createMenu(OWNER_ID, 1L, null);
            mockMvc.perform(owner(put("/v1/stores/1/menus/1"), OWNER_ID).content(menuBody(OTHER_STORE_KEY)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("imageKey"));
        }
    }

    private void createStore(long ownerId) throws Exception {
        mockMvc.perform(owner(post("/v1/stores"), ownerId).content("""
                        {"name":"싹분식","category":"SNACK","address":"서울시 마포구 1","minOrderAmount":12000,"deliveryFee":3000}
                        """))
                .andExpect(status().isCreated());
    }

    private ResultActions issue(long ownerId, long storeId, String body) throws Exception {
        return mockMvc.perform(owner(post("/v1/stores/" + storeId + "/images/upload-url"), ownerId).content(body));
    }

    private ResultActions updateStore(long ownerId, long storeId, String imageKey) throws Exception {
        return mockMvc.perform(owner(put("/v1/stores/" + storeId), ownerId).content("""
                {"name":"싹분식","category":"SNACK","address":"서울시 마포구 1","minOrderAmount":12000,"deliveryFee":3000,"imageKey":%s}
                """.formatted(json(imageKey))));
    }

    private ResultActions createMenu(long ownerId, long storeId, String imageKey) throws Exception {
        return mockMvc.perform(owner(post("/v1/stores/" + storeId + "/menus"), ownerId).content(menuBody(imageKey)));
    }

    private String menuBody(String imageKey) {
        return "{\"name\":\"참치김밥\",\"price\":4500,\"imageKey\":" + json(imageKey) + "}";
    }

    private String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
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
