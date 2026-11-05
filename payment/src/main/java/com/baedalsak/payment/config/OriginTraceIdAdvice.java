package com.baedalsak.payment.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

import java.util.List;

/**
 * 메시지 헤더 traceId(발행 쪽 outbox 에 저장된 요청 당시 traceId)를 MDC originTraceId 로 넣는 리스너 컨테이너 advice (#126).
 * 컨테이너 advice chain 맨 앞(재시도 바깥)에 두어, 앱 안 재시도 내내 유지되고 재시도를 다 쓴 뒤 DLQ 로 보낼 때의 로그에도 찍힌다.
 * 헤더가 없거나 비어 있으면 넣지 않는다. 처리가 끝나면(예외 포함) 이전 값으로 되돌려 다음 메시지에 남지 않게 한다.
 */
public class OriginTraceIdAdvice implements MethodInterceptor {

    public static final String HEADER = "traceId";
    public static final String MDC_KEY = "originTraceId";

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String originTraceId = originTraceId(invocation.getArguments());
        if (originTraceId == null) {
            return invocation.proceed();
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, originTraceId);
        try {
            return invocation.proceed();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }

    // 컨테이너가 넘기는 값은 Message 하나(또는 배치면 Message 목록). 배치는 첫 메시지 기준
    static String originTraceId(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Message message) {
                return headerValue(message);
            }
            if (argument instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Message message) {
                return headerValue(message);
            }
        }
        return null;
    }

    private static String headerValue(Message message) {
        Object value = message.getMessageProperties().getHeader(HEADER);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
