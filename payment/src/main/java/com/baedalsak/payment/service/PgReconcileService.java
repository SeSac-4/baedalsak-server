package com.baedalsak.payment.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baedalsak.payment.client.toss.TossTransaction;
import com.baedalsak.payment.client.toss.TossTransactionClient;
import com.baedalsak.payment.domain.Payment;
import com.baedalsak.payment.domain.PaymentCancel;
import com.baedalsak.payment.domain.PaymentCancelStatus;
import com.baedalsak.payment.domain.PaymentStatus;
import com.baedalsak.payment.dto.response.RecoverResultResponse;
import com.baedalsak.payment.repository.PaymentCancelRepository;
import com.baedalsak.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PG 대사 (#154, 설계서 기능명세서 5.5.1 · '동기 호출' PG 대사). 기간 안의 토스 거래와 결제 상태를 비교해,
 * 안전하게 맞출 수 있는 건(환불 처리 중)은 재처리 서비스(#153)로 자동 보정하고 나머지는 결제마다 ERROR 로그로 알린다.
 * 비교 대상은 거래에 나온 결제 키 + 기간 안에 승인된 결제. 처리 규칙은 사가 대사 노트 10절.
 * 두 파드에서 두 번 돌아도 자동 보정은 조건부 UPDATE · 같은 멱등 키라 결과가 같다 (잠금 없음, 노트 5절).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgReconcileService {

  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final TossTransactionClient tossTransactionClient;
  private final PaymentRepository paymentRepository;
  private final PaymentCancelRepository paymentCancelRepository;
  private final PaymentRecoverService paymentRecoverService;

  /** 결제 하나의 대사 결과 */
  public enum Outcome {
    MATCHED, FIXED, IN_PROGRESS, MISMATCH
  }

  /** 토스 거래로 본 결제 상태 */
  enum TossState {
    APPROVED, CANCELED, NONE
  }

  /** 실행 요약 (INFO 1줄) */
  public record Summary(int compared, Map<Outcome, Integer> counts) {

    public int count(Outcome outcome) {
      return counts.getOrDefault(outcome, 0);
    }
  }

  /**
   * 기간(한국 시각) 대사.
   * @throws com.baedalsak.payment.client.toss.TossApiException 거래 조회 실패 — 이번 실행은 끝, 다음 실행에서 다시
   */
  public Summary reconcile(LocalDateTime start, LocalDateTime end) {
    Map<String, List<TossTransaction>> byKey = tossTransactionClient.getTransactions(start, end).stream()
        .collect(Collectors.groupingBy(TossTransaction::paymentKey, LinkedHashMap::new, Collectors.toList()));

    Map<String, Payment> payments = new LinkedHashMap<>();
    paymentRepository.findByTossPaymentKeyIn(byKey.keySet())
        .forEach(payment -> payments.put(payment.getTossPaymentKey(), payment));
    paymentRepository.findByApprovedAtBetween(start.atZone(SEOUL).toOffsetDateTime(), end.atZone(SEOUL).toOffsetDateTime())
        .forEach(payment -> payments.putIfAbsent(payment.getTossPaymentKey(), payment));

    Set<String> keys = new LinkedHashSet<>(byKey.keySet());
    keys.addAll(payments.keySet());

    Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
    for (String key : keys) {
      Outcome outcome = compare(payments.get(key), byKey.getOrDefault(key, List.of()));
      counts.merge(outcome, 1, Integer::sum);
    }
    Summary summary = new Summary(keys.size(), counts);
    log.info("PG 대사 완료 기간={}~{}, 비교={}, 일치={}, 자동 보정={}, 처리 중={}, 불일치={}",
        start, end, summary.compared(), summary.count(Outcome.MATCHED), summary.count(Outcome.FIXED),
        summary.count(Outcome.IN_PROGRESS), summary.count(Outcome.MISMATCH));
    return summary;
  }

  Outcome compare(Payment payment, List<TossTransaction> transactions) {
    TossState toss = tossState(transactions);
    if (payment == null) {
      // 결제 키는 로그에 남기지 않고 토스 주문번호로 찾는다
      log.error("PG 대사 불일치 DB 에 결제 없음 tossOrderId={}, tossStatus={}", transactions.get(0).orderId(), toss);
      return Outcome.MISMATCH;
    }
    return switch (payment.getStatus()) {
      case APPROVED -> compareApproved(payment, toss, transactions);
      case CANCELED -> toss == TossState.CANCELED ? Outcome.MATCHED : mismatch(payment, toss, "환불 완료인데 토스 취소 거래 없음");
      case CANCEL_REQUESTED -> reconcileCancel(payment, toss);
      case EXPIRED -> toss == TossState.APPROVED ? mismatch(payment, toss, "결제만료인데 토스 승인") : Outcome.MATCHED;
      case REQUESTED -> mismatch(payment, toss, "결제 요청됨인데 토스 거래 있음");
    };
  }

  private Outcome compareApproved(Payment payment, TossState toss, List<TossTransaction> transactions) {
    if (toss != TossState.APPROVED) {
      return mismatch(payment, toss, toss == TossState.CANCELED ? "승인 완료인데 토스 취소" : "승인 완료인데 토스 거래 없음");
    }
    int tossAmount = transactions.stream().filter(TossTransaction::isApproval).mapToInt(TossTransaction::amount).sum();
    if (tossAmount != payment.getAmount()) {
      return mismatch(payment, toss, "금액 다름 db=" + payment.getAmount() + ", toss=" + tossAmount);
    }
    return Outcome.MATCHED;
  }

  // 환불 처리 중: 확정 거절(FAILED) 뒤 토스가 취소가 아니면 새 키 취소라 사람이 재처리 API, 나머지는 재처리 서비스로 자동 보정
  // (재처리 서비스는 FAILED + 토스 승인에서 새 키로 다시 취소하므로 대사에서는 부르지 않는다)
  private Outcome reconcileCancel(Payment payment, TossState toss) {
    Optional<PaymentCancel> latest = paymentCancelRepository.findTopByPaymentIdOrderByIdDesc(payment.getId());
    if (toss != TossState.CANCELED && latest.map(c -> c.getStatus() == PaymentCancelStatus.FAILED).orElse(false)) {
      return mismatch(payment, toss, "취소 확정 거절 뒤 토스 취소 아님 — 재처리 API 로 다시 취소 필요");
    }
    RecoverResultResponse result;
    try {
      result = paymentRecoverService.recover(payment.getId());
    } catch (RuntimeException e) {
      return mismatch(payment, toss, "재처리 실패 " + e.getMessage());
    }
    return switch (result.action()) {
      case RECORDED_CANCELED, CANCELED -> {
        log.info("PG 대사 자동 보정 paymentId={}, orderId={}, action={}, after={}",
            payment.getId(), payment.getOrderId(), result.action(), result.after());
        yield Outcome.FIXED;
      }
      case IN_PROGRESS -> Outcome.IN_PROGRESS;
      default -> mismatch(payment, toss, "재처리 결과 " + result.action());
    };
  }

  // 같은 결제 키에 취소 거래가 있으면 취소, 승인 거래만 있으면 승인 (전액 취소만 쓴다)
  static TossState tossState(List<TossTransaction> transactions) {
    if (transactions.stream().anyMatch(TossTransaction::isCancel)) {
      return TossState.CANCELED;
    }
    return transactions.stream().anyMatch(TossTransaction::isApproval) ? TossState.APPROVED : TossState.NONE;
  }

  private Outcome mismatch(Payment payment, TossState toss, String reason) {
    log.error("PG 대사 불일치 paymentId={}, orderId={}, dbStatus={}, tossStatus={}, reason={}",
        payment.getId(), payment.getOrderId(), payment.getStatus(), toss, reason);
    return Outcome.MISMATCH;
  }
}
