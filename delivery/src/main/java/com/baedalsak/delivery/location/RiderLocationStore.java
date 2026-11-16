package com.baedalsak.delivery.location;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 캐시 Redis 의 실시간 위치 · 가상 GPS 상태 (배달실시간초안 7번). 지워져도 다시 채워지는 값만 둔다.
 * <ul>
 *   <li>{@code delivery:location:{id}} — 라이더 위치 JSON (RiderLocation). 쓸 때마다 TTL 갱신</li>
 *   <li>{@code delivery:sim:{id}} — 가상 GPS 시작 시각(epoch ms). 파드가 다시 떠도 경과 시간으로 이어서 움직인다</li>
 *   <li>{@code delivery:sim:active} — 가상 GPS 가 돌고 있는 배달 ID 집합</li>
 *   <li>{@code delivery:rider-position:{riderId}} — 라이더 현재 위치 JSON (GeoPoint). 배달과 무관해 TTL 없음 —
 *       배달을 마친 자리에 계속 남는다. 지워지면 다음 조회 때 새 가상 위치를 만든다</li>
 * </ul>
 * Redis 장애가 배달 API 로 번지지 않도록 모든 메서드는 예외를 던지지 않는다 (실패 시 빈 값 · false).
 * 접속이 안 되면 경고를 한 번만 남기고 {@link #RETRY_AFTER} 동안 Redis 를 부르지 않는다
 * (캐시 Redis 가 없는 환경에서 가상 GPS 가 2초마다 실패 로그를 쌓지 않게).
 */
@Slf4j
@Component
public class RiderLocationStore {

    static final String ACTIVE_SIMULATIONS_KEY = "delivery:sim:active";
    /** 접속 실패 후 다시 시도하기까지 쉬는 시간 */
    static final Duration RETRY_AFTER = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final Duration ttl;
    private final Clock clock;
    /** 이 시각 전까지는 Redis 를 부르지 않는다. null 이면 정상 */
    private volatile Instant unavailableUntil;

    public RiderLocationStore(StringRedisTemplate redis, JsonMapper jsonMapper, DeliveryGpsProperties properties,
                              Clock clock) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.ttl = properties.locationTtl();
        this.clock = clock;
    }

    static String locationKey(Long deliveryId) {
        return "delivery:location:" + deliveryId;
    }

    static String simulationKey(Long deliveryId) {
        return "delivery:sim:" + deliveryId;
    }

    static String riderPositionKey(Long riderId) {
        return "delivery:rider-position:" + riderId;
    }

    /**
     * 위치를 저장하고 TTL 을 다시 건다.
     *
     * @param deliveryId 배달 ID
     * @param location   위치
     */
    public void save(Long deliveryId, RiderLocation location) {
        call("location save", deliveryId, () -> {
            redis.opsForValue().set(locationKey(deliveryId), jsonMapper.writeValueAsString(location), ttl);
            return null;
        }, null);
    }

    /**
     * @param deliveryId 배달 ID
     * @return 마지막 위치. 없거나 Redis 실패면 빈 값
     */
    public Optional<RiderLocation> find(Long deliveryId) {
        return call("location read", deliveryId, () -> {
            String json = redis.opsForValue().get(locationKey(deliveryId));
            return json == null ? Optional.<RiderLocation>empty()
                    : Optional.of(jsonMapper.readValue(json, RiderLocation.class));
        }, Optional.empty());
    }

    /**
     * 가상 GPS 를 시작한다. 이미 시작돼 있으면 처음 시작 시각을 유지한다 (SET NX).
     *
     * @param deliveryId 배달 ID
     * @param startedAt  시작 시각 (픽업 시각)
     * @return 저장에 성공했거나 이미 있으면 true
     */
    public boolean startSimulation(Long deliveryId, Instant startedAt) {
        return call("simulation start", deliveryId, () -> {
            redis.opsForValue().setIfAbsent(simulationKey(deliveryId), String.valueOf(startedAt.toEpochMilli()), ttl);
            redis.opsForSet().add(ACTIVE_SIMULATIONS_KEY, String.valueOf(deliveryId));
            return true;
        }, false);
    }

    /**
     * @param deliveryId 배달 ID
     * @return 가상 GPS 시작 시각. 없으면 빈 값
     */
    public Optional<Instant> findSimulationStart(Long deliveryId) {
        return call("simulation read", deliveryId, () -> {
            String value = redis.opsForValue().get(simulationKey(deliveryId));
            if (value == null) {
                return Optional.<Instant>empty();
            }
            // 위치를 계속 쓰는 동안은 시작 시각도 남아 있어야 하므로 같이 연장한다
            redis.expire(simulationKey(deliveryId), ttl);
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));
        }, Optional.empty());
    }

    /**
     * @return 가상 GPS 가 돌고 있는 배달 ID. Redis 실패면 빈 집합
     */
    public Set<Long> activeSimulations() {
        return call("simulation list", null, () -> {
            Set<String> members = redis.opsForSet().members(ACTIVE_SIMULATIONS_KEY);
            if (members == null) {
                return Set.<Long>of();
            }
            return members.stream().map(Long::valueOf).collect(Collectors.toUnmodifiableSet());
        }, Set.of());
    }

    /**
     * 가상 GPS 만 멈춘다. 마지막 위치는 남겨 둔다 (실제 GPS 로 바뀌었을 때 등).
     *
     * @param deliveryId 배달 ID
     */
    public void stopSimulation(Long deliveryId) {
        call("simulation stop", deliveryId, () -> {
            redis.delete(simulationKey(deliveryId));
            redis.opsForSet().remove(ACTIVE_SIMULATIONS_KEY, String.valueOf(deliveryId));
            return null;
        }, null);
    }

    /**
     * 배달이 끝났을 때 위치 · 가상 GPS 상태를 모두 지운다. 지우지 못해도 위치 키는 TTL 로 사라진다.
     *
     * @param deliveryId 배달 ID
     */
    public void clear(Long deliveryId) {
        call("location clear", deliveryId, () -> {
            redis.delete(Set.of(simulationKey(deliveryId), locationKey(deliveryId)));
            redis.opsForSet().remove(ACTIVE_SIMULATIONS_KEY, String.valueOf(deliveryId));
            return null;
        }, null);
    }

    /**
     * @param riderId 라이더 ID
     * @return 라이더 현재 위치 (배달과 무관). 없거나 Redis 실패면 빈 값
     */
    public Optional<GeoPoint> findRiderPosition(Long riderId) {
        return call("rider position read", riderId, () -> {
            String json = redis.opsForValue().get(riderPositionKey(riderId));
            return json == null ? Optional.<GeoPoint>empty()
                    : Optional.of(jsonMapper.readValue(json, GeoPoint.class));
        }, Optional.empty());
    }

    /**
     * 라이더 위치가 없을 때만 저장한다 (SET NX). 동시에 저장하려 해도 먼저 저장된 값 하나만 남는다.
     *
     * @param riderId 라이더 ID
     * @param point   저장할 위치
     * @return 저장 후 남아 있는 위치 (이미 있었으면 그 값). Redis 실패면 빈 값
     */
    public Optional<GeoPoint> saveRiderPositionIfAbsent(Long riderId, GeoPoint point) {
        return call("rider position create", riderId, () -> {
            String key = riderPositionKey(riderId);
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, jsonMapper.writeValueAsString(point)))) {
                return Optional.of(point);
            }
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.<GeoPoint>empty()
                    : Optional.of(jsonMapper.readValue(json, GeoPoint.class));
        }, Optional.empty());
    }

    /**
     * 라이더 위치를 덮어쓴다 (배달을 마친 자리 등).
     *
     * @param riderId 라이더 ID
     * @param point   위치
     */
    public void saveRiderPosition(Long riderId, GeoPoint point) {
        call("rider position save", riderId, () -> {
            redis.opsForValue().set(riderPositionKey(riderId), jsonMapper.writeValueAsString(point));
            return null;
        }, null);
    }

    /**
     * Redis 호출 공통 처리. 쉬는 중이면 부르지 않고 fallback 을 돌려준다.
     * 접속 실패(DataAccessException)는 처음 한 번만 경고하고 RETRY_AFTER 동안 쉰다. 그 밖의 실패는 그때마다 경고한다.
     */
    private <T> T call(String action, Long id, Supplier<T> operation, T fallback) {
        Instant until = unavailableUntil;
        if (until != null && clock.instant().isBefore(until)) {
            return fallback;
        }
        try {
            T result = operation.get();
            if (until != null) {
                unavailableUntil = null;
                log.info("cache redis recovered");
            }
            return result;
        } catch (DataAccessException e) {
            unavailableUntil = clock.instant().plus(RETRY_AFTER);
            if (until == null) {
                log.warn("cache redis unavailable, location disabled for {}s: action={}, cause={}",
                        RETRY_AFTER.toSeconds(), action, e.toString());
            }
            return fallback;
        } catch (RuntimeException e) {
            log.warn("{} failed: id={}, cause={}", action, id, e.toString());
            return fallback;
        }
    }
}
