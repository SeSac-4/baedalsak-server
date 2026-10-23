package com.baedalsak.common.auth;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonAuthAutoConfiguration implements WebMvcConfigurer {

  // Spring MVC에 "이 resolver도 써 줘"라고 추가
  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new LoginMemberArgumentResolver());
  }

  // Filter 타입 Bean은 Spring Boot가 자동으로 필터 체인에 등록해 준다
  @Bean
  public MemberMdcFilter memberMdcFilter() {
    return new MemberMdcFilter();
  }
}
