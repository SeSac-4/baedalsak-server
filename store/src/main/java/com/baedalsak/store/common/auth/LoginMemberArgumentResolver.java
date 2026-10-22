package com.baedalsak.store.common.auth;

import com.baedalsak.store.common.error.BusinessException;
import com.baedalsak.store.common.error.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @CurrentMember LoginMember 파라미터를 X-Member-Id, X-Member-Role 헤더로 채운다.
 */
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && LoginMember.class.equals(parameter.getParameterType());
    }

    @Override
    public LoginMember resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String id = webRequest.getHeader(LoginMember.ID_HEADER);
        String role = webRequest.getHeader(LoginMember.ROLE_HEADER);
        if (id == null || role == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return new LoginMember(Long.valueOf(id), MemberRole.valueOf(role));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
