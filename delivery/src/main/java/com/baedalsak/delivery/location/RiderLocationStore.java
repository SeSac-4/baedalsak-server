package com.baedalsak.delivery.location;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 캐시 Redis 의 실시간 위치 · 가상 GPS 상태 (배달실시간초안 7번). 지워져도 다시 채워지는 값만 둔다.
 * <ul>
 *   <li>{@code delivery:location:{id}} — 라이더 위치 JSON (RiderLocation). 쓸 때마다 TTL 갱신</li>
 *   <li>{@code delivery:sim:{id}} — 가상 GPS 시작 시각(epoch ms). 파드가 다시 떠도 경과 시간으로 이어서 움직인다</li>
 *   <li>{@code delivery:sim:active} — 가상 GPS 가 돌고 있는 배달 ID 집합</li>
 * </ul>
 * Redis 장애가 배달 API 로 번지지 않도록 모든 메서드는 예외를 던지지 않는다 (실패 시 빈 값 · false).
 */
@Slf4j
@Component
public class RiderLocationStore {

    static final String ACTIVE_SIMULATIONS_KEY = "delivery:sim:active";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final Duration ttl;

    public RiderLocationStore(StringRedisTemplate redis, JsonMapper jsonMapper, DeliveryGpsProperties properties) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.ttl = properties.locationTtl();
    }

    static String locationKey(Long deliveryId) {
        return "delivery:location:" + deliveryId;
    }

    static String simulationKey(Long deliveryId) {
        return "delivery:sim:" + deliveryId;
    }

    /**
     * 위치를 저장하고 TTL 을 다시 건다.
     *
     * @param deliveryId 배달 ID
     * @param location   위치
     */
    public void save(Long deliveryId, RiderLocation location) {
        try {
            redis.opsForValue().set(locationKey(deliveryId), jsonMapper.writeValueAsString(location), ttl);
        } catch (RuntimeException e) {
            log.warn("location save failed: deliveryId={}, cause={}", deliveryId, e.toString());
        }
    }

    /**
     * @param deliveryId 배달 ID
     * @return 마지막 위치. 없거나 Redis 실패면 빈 값
     */
    public Optional<RiderLocation> find(Long deliveryId) {
        try {
            String json = redis.opsForValue().get(locationKey(deliveryId));
            return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, RiderLocation.class));
        } catch (RuntimeException e) {
            log.warn("location read failed: deliveryId={}, cause={}", deliveryId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 가상 GPS 를 시작한다. 이미 시작돼 있으면 처음 시작 시각을 유지한다 (SET NX).
     *
     * @param deliveryId 배달 ID
     * @param startedAt  시작 시각 (픽업 시각)
     * @return 저장에 성공했거나 이미 있으면 true
     */
    public boolean startSimulation(Long deliveryId, Instant startedAt) {
        try {
            redis.opsForValue().setIfAbsent(simulationKey(deliveryId), String.valueOf(startedAt.toEpochMilli()), ttl);
            redis.opsForSet().add(ACTIVE_SIMULATIONS_KEY, String.valueOf(deliveryId));
            return true;
        } catch (RuntimeException e) {
            log.warn("simulation start failed: deliveryId={}, cause={}", deliveryId, e.toString());
            return false;
        }
    }

    /**
     * @param deliveryId 배달 ID
     * @return 가상 GPS 시작 시각. 없으면 빈 값
     */
    public Optional<Instant> findSimulationStart(Long deliveryId) {
        try {
            String value = redis.opsForValue().get(simulationKey(deliveryId));
            if (value == null) {
                return Optional.empty();
            }
            // 위치를 계속 쓰는 동안은 시작 시각도 남아 있어야 하므로 같이 연장한다
            redis.expire(simulationKey(deliveryId), ttl);
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));
        } catch (RuntimeException e) {
            log.warn("simulation read failed: deliveryId={}, cause={}", deliveryId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * @return 가상 GPS 가 돌고 있는 배달 ID. Redis 실패면 빈 집합
     */
    public Set<Long> activeSimulations() {
        try {
            Set<String> members = redis.opsForSet().members(ACTIVE_SIMULATIONS_KEY);
            if (members == null) {
                return Set.of();
            }
            return members.stream().map(Long::valueOf).collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException e) {
            log.warn("simulation list failed: cause={}", e.toString());
            return Set.of();
        }
    }

    /**
     * 가상 GPS 만 멈춘다. 마지막 위치는 남겨 둔다 (실제 GPS 로 바뀌었을 때 등).
     *
     * @param deliveryId 배달 ID
     */
    public void stopSimulation(Long deliveryId) {
        try {
            redis.delete(simulationKey(deliveryId));
            redis.opsForSet().remove(ACTIVE_SIMULATIONS_KEY, String.valueOf(deliveryId));
        } catch (RuntimeException e) {
            log.warn("simulation stop failed: deliveryId={}, cause={}", deliveryId, e.toString());
        }
    }

    /**
     * 배달이 끝났을 때 위치 · 가상 GPS 상태를 모두 지운다.
     *
     * @param deliveryId 배달 ID
     */
    public void clear(Long deliveryId) {
        stopSimulation(deliveryId);
        try {
            redis.delete(locationKey(deliveryId));
        } catch (RuntimeException e) {
            log.warn("location clear failed, expires by TTL: deliveryId={}, cause={}", deliveryId, e.toString());
        }
    }
}
