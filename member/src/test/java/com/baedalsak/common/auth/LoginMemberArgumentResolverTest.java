package com.baedalsak.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.baedalsak.common.error.CommonErrorCode;

/**
 * @CurrentMember 헤더 해석 (#112 T47). gateway 가 넣은 X-Member-Id · X-Member-Role 을 LoginMember 로 바꾼다.
 * common 은 웹 의존성이 compileOnly 라 테스트를 돌릴 수 없어서, 이 부품을 실제로 쓰는 member 모듈에서 테스트한다
 * (CI 는 서비스 모듈의 test 만 돌린다).
 */
class LoginMemberArgumentResolverTest {

  private final LoginMemberArgumentResolver resolver = new LoginMemberArgumentResolver();

  /** 컨트롤러 메서드 모양을 흉내 낸 것 — 파라미터 0: @CurrentMember LoginMember, 1: 그냥 LoginMember, 2: @CurrentMember String */
  @SuppressWarnings("unused")
  static class SampleController {
    void handle(@CurrentMember LoginMember member, LoginMember noAnnotation, @CurrentMember String wrongType) {
    }
  }

  private static MethodParameter parameter(int index) throws NoSuchMethodException {
    Method method = SampleController.class.getDeclaredMethod("handle", LoginMember.class, LoginMember.class, String.class);
    return new MethodParameter(method, index);
  }

  /** gateway 가 헤더를 넣어 보낸 요청 (null 이면 그 헤더 없음) */
  private static ServletWebRequest request(String memberId, String role) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (memberId != null) {
      request.addHeader(LoginMember.ID_HEADER, memberId);
    }
    if (role != null) {
      request.addHeader(LoginMember.ROLE_HEADER, role);
    }
    return new ServletWebRequest(request);
  }

  private LoginMember resolve(String memberId, String role) throws Exception {
    return resolver.resolveArgument(parameter(0), null, request(memberId, role), null);
  }

  @Test
  void CurrentMember_가_붙은_LoginMember_만_채운다() throws Exception {
    assertThat(resolver.supportsParameter(parameter(0))).isTrue();
    assertThat(resolver.supportsParameter(parameter(1))).isFalse();   // 어노테이션 없음
    assertThat(resolver.supportsParameter(parameter(2))).isFalse();   // 타입이 LoginMember 아님
  }

  @Test
  void 헤더_두_개로_로그인_회원을_만든다() throws Exception {
    LoginMember member = resolve("101", "OWNER");

    assertThat(member.id()).isEqualTo(101L);
    assertThat(member.role()).isEqualTo(MemberRole.OWNER);
  }

  @Test
  void 헤더가_없으면_401() {
    assertThatThrownBy(() -> resolve(null, null))
        .extracting("errorCode").isEqualTo(CommonErrorCode.UNAUTHORIZED);
    assertThatThrownBy(() -> resolve("101", null))
        .extracting("errorCode").isEqualTo(CommonErrorCode.UNAUTHORIZED);
  }

  @Test
  void 회원_ID_가_숫자가_아니면_401() {
    assertThatThrownBy(() -> resolve("abc", "CUSTOMER"))
        .extracting("errorCode").isEqualTo(CommonErrorCode.UNAUTHORIZED);
  }

  @Test
  void 없는_역할이면_401() {
    assertThatThrownBy(() -> resolve("1", "ADMIN"))
        .extracting("errorCode").isEqualTo(CommonErrorCode.UNAUTHORIZED);
  }
}
