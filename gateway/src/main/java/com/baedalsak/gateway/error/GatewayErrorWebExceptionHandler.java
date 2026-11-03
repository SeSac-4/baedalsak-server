package com.baedalsak.gateway.error;

import java.net.ConnectException;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.common.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * gateway 에서 난 예외(404·405·뒤 서비스 연결 실패 등)를 공통 에러 포맷으로 (#112 T43).
 * Spring 기본 처리기(@Order(-1))보다 먼저 잡는다.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

  private final GatewayErrorWriter writer;

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    ErrorCode code = toErrorCode(ex);
    if (code.getStatus() >= 500) {
      // 서버 쪽 문제 → 원인 추적용 스택까지
      log.error("gateway 처리 실패 code={} path={}", code.name(), exchange.getRequest().getPath(), ex);
    } else {
      // 요청이 잘못됨 → 한 줄만
      log.warn("gateway 요청 오류 code={} path={}", code.name(), exchange.getRequest().getPath());
    }
    return writer.write(exchange, code);
  }

  private ErrorCode toErrorCode(Throwable ex) {
    if (ex instanceof ConnectException) {   // 뒤 서비스가 꺼져 있음
      return CommonErrorCode.DEPENDENCY_UNAVAILABLE;
    }
    if (ex instanceof ResponseStatusException rse) {
      return switch (rse.getStatusCode().value()) {
        case 404 -> CommonErrorCode.NOT_FOUND;
        case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
        case 503, 504 -> CommonErrorCode.DEPENDENCY_UNAVAILABLE;   // 뒤 서비스 응답 없음·지연
        default -> rse.getStatusCode().is4xxClientError()
            ? CommonErrorCode.VALIDATION_FAILED : CommonErrorCode.INTERNAL_ERROR;
      };
    }
    return CommonErrorCode.INTERNAL_ERROR;
  }
}