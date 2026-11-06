package com.baedalsak.delivery.location;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 위치 저장소: JSON 왕복 · TTL, Redis 장애 시 예외 없이 빈 값
 */
class RiderLocationStoreTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final RiderLocationStore store = new RiderLocationStore(redis, jsonMapper,
            new DeliveryGpsProperties(DeliveryGpsProperties.Mode.VIRTUAL, Duration.ofSeconds(2), 18, 30, TTL));

    @Test
    void savesJsonWithTtlAndReadsItBack() {
        when(redis.opsForValue()).thenReturn(values);
        OffsetDateTime at = OffsetDateTime.of(2026, 11, 6, 12, 0, 0, 0, ZoneOffset.ofHours(9));
        RiderLocation location = new RiderLocation(37.5, 127.0, 120, at, false, null, at, LocationSource.VIRTUAL);

        store.save(7L, location);

        var json = org.mockito.ArgumentCaptor.forClass(String.class);
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
        assertThat(store.startSimulation(7L, java.time.Instant.now())).isFalse();
    }
}
