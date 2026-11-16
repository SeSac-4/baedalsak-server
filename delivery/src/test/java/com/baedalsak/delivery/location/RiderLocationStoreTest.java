package com.baedalsak.delivery.location;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 위치 저장소: JSON 왕복 · TTL, Redis 장애 시 예외 없이 빈 값, 접속 실패 후 30초 쉬기
 */
class RiderLocationStoreTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> sets = mock(SetOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final MutableClock clock = new MutableClock(NOW);
    private final RiderLocationStore store = new RiderLocationStore(redis, jsonMapper,
            new DeliveryGpsProperties(DeliveryGpsProperties.Mode.VIRTUAL, Duration.ofSeconds(2), new DeliveryGpsProperties.SpeedKmh(3.6, 9, 18), 30, TTL), clock);

    @Test
    void savesJsonWithTtlAndReadsItBack() {
        when(redis.opsForValue()).thenReturn(values);
        OffsetDateTime at = OffsetDateTime.of(2026, 11, 6, 12, 0, 0, 0, ZoneOffset.ofHours(9));
        RiderLocation location = new RiderLocation(37.5, 127.0, 120, at, false, null, at, LocationSource.VIRTUAL);

        store.save(7L, location);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("delivery:location:7"), json.capture(), eq(TTL));
        when(values.get("delivery:location:7")).thenReturn(json.getValue());
        // 시간대 표기는 읽는 쪽 설정에 따라 달라질 수 있어 같은 시점인지로 비교한다
        RiderLocation read = store.find(7L).orElseThrow();
        assertThat(read.etaAt()).isAtSameInstantAs(at);
        assertThat(read.updatedAt()).isAtSameInstantAs(at);
        assertThat(read.remainingDistanceM()).isEqualTo(120);
        assertThat(read.point()).isEqualTo(location.point());
        assertThat(read.source()).isEqualTo(LocationSource.VIRTUAL);
    }

    @Test
    void returnsEmptyWhenRedisIsDown() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(store.find(7L)).isEmpty();
        assertThat(store.findSimulationStart(7L)).isEmpty();
    }

    @Test
    void saveDoesNotThrowWhenRedisIsDown() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        RiderLocation location = new RiderLocation(37.5, 127.0, null, null, false, null,
                OffsetDateTime.now(), LocationSource.REAL);

        store.save(7L, location);   // 예외 없이 끝나면 통과
        assertThat(store.startSimulation(7L, Instant.now())).isFalse();
    }

    @Test
    void skipsRedisForRetryPeriodAfterConnectionFailure() {
        when(redis.opsForSet()).thenReturn(sets);
        when(sets.members(RiderLocationStore.ACTIVE_SIMULATIONS_KEY))
                .thenThrow(new RedisConnectionFailureException("down"));

        // 2초마다 도는 가상 GPS 가 여러 번 불러도 Redis 는 처음 한 번만 호출된다
        for (int i = 0; i < 10; i++) {
            assertThat(store.activeSimulations()).isEmpty();
            clock.advance(Duration.ofSeconds(2));
        }
        verify(sets, times(1)).members(RiderLocationStore.ACTIVE_SIMULATIONS_KEY);
    }

    @Test
    void retriesAfterPeriodAndRecovers() {
        when(redis.opsForSet()).thenReturn(sets);
        when(sets.members(RiderLocationStore.ACTIVE_SIMULATIONS_KEY))
                .thenThrow(new RedisConnectionFailureException("down"));
        store.activeSimulations();

        reset(sets);
        when(sets.members(RiderLocationStore.ACTIVE_SIMULATIONS_KEY)).thenReturn(Set.of("3"));
        clock.advance(RiderLocationStore.RETRY_AFTER.plusSeconds(1));

        assertThat(store.activeSimulations()).containsExactly(3L);
        assertThat(store.activeSimulations()).containsExactly(3L);   // 복구 후에는 매번 호출
        verify(sets, times(2)).members(RiderLocationStore.ACTIVE_SIMULATIONS_KEY);
    }

    /** 테스트에서 시간을 앞으로 돌리는 시계 */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
