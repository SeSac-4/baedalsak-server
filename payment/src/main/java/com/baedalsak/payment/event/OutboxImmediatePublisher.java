package com.baedalsak.payment.event;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 커밋 직후 즉시 발행 (#151, 사가 결정 1-1 #14 — order #143 과 같은 방식). 1초 폴링(OutboxPublisher)은 그대로 두어 실패한 행을 재시도한다.
 * 발행 확인을 기다리는 동안 내부 승인 응답 · 취소 명령 처리가 늦어지지 않게 별도 스레드에서 보낸다.
 *
 * <p>실행 중 1개 + 대기 1개만 둔다. 대기 중인 실행이 있으면 그 실행이 지금 커밋된 행까지 보내므로 추가 신호는 버려도 된다.
 * 폴링과 겹쳐도 FOR UPDATE SKIP LOCKED 라 같은 행을 동시에 보내지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxImmediatePublisher {

  private final OutboxPublisher outboxPublisher;
  private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(1),
      task -> new Thread(task, "outbox-immediate"),
      new ThreadPoolExecutor.DiscardPolicy());

  public OutboxImmediatePublisher(OutboxPublisher outboxPublisher) {
    this.outboxPublisher = outboxPublisher;
  }

  // 롤백이면 불리지 않는다 (AFTER_COMMIT)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommitted(OutboxAppended event) {
    executor.execute(this::publish);
  }

  // 실패해도 행은 미발행으로 남아 폴링이 다시 보낸다
  private void publish() {
    try {
      outboxPublisher.publishPending();
    } catch (RuntimeException e) {
      log.warn("outbox 즉시 발행 실패, 폴링이 재시도 cause={}", e.toString());
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }
}
