package com.baedalsak.delivery.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 요청 헤더에서 LoginMember 를 만들어 넣어 준다.
 * 헤더가 없거나 잘못되면 401 UNAUTHORIZED.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {
}
