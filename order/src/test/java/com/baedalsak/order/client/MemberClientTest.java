package com.baedalsak.order.client;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * member [내부] API 클라이언트. 응답 모양은 설계서 '서비스 경계' 탭 MemberInternal (name, phone, email, role)
 */
class MemberClientTest {

    MockRestServiceServer server;
    MemberClient memberClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://member");
        server = MockRestServiceServer.bindTo(builder).build();
        memberClient = new MemberClient(builder.build());
    }

    @Test
    void 회원_연락처와_이메일을_받는다() {
        server.expect(requestTo("http://member/internal/members/7"))
                .andRespond(withSuccess("""
                        {"name":"김고객","phone":"010-1234-5678","email":"customer@baedalsak.click","role":"CUSTOMER"}
                        """, MediaType.APPLICATION_JSON));

        MemberInternal member = memberClient.getMember(7L).orElseThrow();

        assertThat(member.phone()).isEqualTo("010-1234-5678");
        assertThat(member.email()).isEqualTo("customer@baedalsak.click");
        assertThat(member.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void 로그에는_휴대폰_번호가_마스킹된다() {
        MemberInternal member = new MemberInternal("김고객", "010-1234-5678", "c@baedalsak.click", "CUSTOMER");

        assertThat(member.toString()).contains("010-****-5678").doesNotContain("1234");
    }

    @Test
    void 회원이_없으면_빈_값() {
        server.expect(requestTo("http://member/internal/members/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(memberClient.getMember(999L)).isEmpty();
    }

    @Test
    void member_가_응답하지_않으면_503() {
        server.expect(requestTo("http://member/internal/members/7"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> memberClient.getMember(7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
}
