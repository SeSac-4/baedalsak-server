package com.baedalsak.payment.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 결제 확인 한 번의 시간 예산 (#125). 토스 호출(승인 · 재전송 · 결제 조회)을 시작하기 전에 남은 시간이
 * 호출 1건 최대(연결 + 읽기)보다 적으면 호출하지 않는다. 그래서 호출 횟수와 관계없이 예산 안에 응답하고,
 * 호출하는 order(제한 17초)와 프론트(25초)보다 먼저 끝난다. 호출하지 않은 경우는 결과 모름(502)으로 응답하고 키는 남긴다.
 */
final class ConfirmBudget {

    private final Clock clock;
    private final Instant deadline;
    private final Duration maxCallTime;

    private ConfirmBudget(Clock clock, Duration budget, Duration maxCallTime) {
        this.clock = clock;
        this.deadline = clock.instant().plus(budget);
        this.maxCallTime = maxCallTime;
    }

    static ConfirmBudget start(Clock clock, Duration budget, Duration maxCallTime) {
        return new ConfirmBudget(clock, budget, maxCallTime);
    }

    /** 지금 토스 호출 1건을 시작해도 예산 안에 끝나는지 */
    boolean canCall() {
        return !clock.instant().plus(maxCallTime).isAfter(deadline);
    }

    Duration remaining() {
        return Duration.between(clock.instant(), deadline);
    }
}
