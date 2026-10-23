package com.baedalsak.common.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;

//@CurrentMember LoginMember 파라미터를 2개의 헤더로 채운다!
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

  //1) 이 파라미터 채울 수 있나?
  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    
    return parameter.hasParameterAnnotation(CurrentMember.class)
    && LoginMember.class.equals(parameter.getParameterType());
  }



  // 2) 실제로 값 만들기
    @Override
    public LoginMember resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String id = webRequest.getHeader(LoginMember.ID_HEADER);
        String role = webRequest.getHeader(LoginMember.ROLE_HEADER);
        if (id == null || role == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        try {
            return new LoginMember(Long.valueOf(id), MemberRole.valueOf(role));
        } catch (IllegalArgumentException e) {
            // "abc"처럼 숫자가 아니거나, ADMIN처럼 없는 역할이면 여기로 옴
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
