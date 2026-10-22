package com.baedalsak.common.auth;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//memberId를 MDC에 넣어 로그에 함께 찍히도록
public class MemberMdcFilter extends OncePerRequestFilter {
  
  private  static final String MDC_KEY = "memberId";

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
            MDC.remove(MDC_KEY);   // 스레드가 재사용되니 꼭 지운다 (안 지우면 다음 요청에 남의 ID가 찍힘)
        }
    }
}
