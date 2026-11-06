package com.baedalsak.payment.event;

/**
 * 트랜잭션 안에서 outbox 행을 썼다는 앱 내부 신호 (#151). 커밋된 뒤에만 OutboxImmediatePublisher 가 받는다.
 */
public record OutboxAppended() {
}
