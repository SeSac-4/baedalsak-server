package com.baedalsak.store.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청자 ID 를 MDC(memberId)에 넣어 로그에 함께 찍히게 한다 (docs 6-1 필수 필드: 사용자 ID).
 * 스레드가 재사용되므로 요청이 끝나면 반드시 지운다.
 */
@Component
public class MemberMdcFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "memberId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String memberId = request.getHeader(LoginMember.ID_HEADER);
        if (memberId != null) {
            MDC.put(MDC_KEY, memberId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
