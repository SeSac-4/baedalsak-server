package com.baedalsak.order.event;

import com.baedalsak.order.domain.OutboxEvent;
import com.baedalsak.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상태 변경과 같은 트랜잭션에 outbox 행을 쓴다 (docs 3-2). 실제 RabbitMQ 발행은 OutboxPublisher 가 한다.
 * 커밋되면 OutboxImmediatePublisher 가 바로 발행을 시작한다 (사가 결정 1-1 #14).
 * payload 는 Boot 가 설정한 JsonMapper 로 직렬화한다 (시각은 KST +09:00).
 */
@Component
@RequiredArgsConstructor
public class OutboxAppender {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;

    // 호출한 쪽 트랜잭션 안에서만 쓴다 (트랜잭션 없이 부르면 예외)
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent append(String eventType, String routingKey, Long aggregateId, Object payload) {
        OutboxEvent row = outboxRepository.save(OutboxEvent.create(eventType, routingKey, aggregateId,
                jsonMapper.writeValueAsString(payload), MDC.get("traceId")));
        eventPublisher.publishEvent(new OutboxAppended());
        return row;
    }
}
