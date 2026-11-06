# delivery 실시간 위치 — curl 로 테스트하기

픽업한 뒤 라이더 위치를 지도에 보여 주는 기능입니다. 고객 · 사장님 · 라이더 화면이 같은 API 를 2초마다 폴링합니다.

| 항목 | 내용 |
|---|---|
| 경로 | `GET /v1/deliveries/by-order/{orderId}/route`. 픽업 후 카카오 길찾기 경로 `[{lat,lng}]`, 거리, 시간. 픽업 전이면 204 |
| 위치 | `GET /v1/deliveries/by-order/{orderId}/location`. 배달 상태 + 라이더 위치 · 남은 거리 · 도착 예정 · 도착 여부. 배달이 없으면 204 |
| 실제 GPS | `PUT /v1/deliveries/{deliveryId}/location` `{lat, lng}`. 배정된 라이더 본인, 픽업 중일 때만 |
| 조회 권한 | 주문 고객 · 가게 사장님 · 배정된 라이더 (gateway 는 세 역할 모두 통과, 소유 확인은 delivery) |
| 위치 저장 | 캐시 Redis `delivery:location:{id}` (TTL 30분). 배달 완료가 커밋되면 지운다 |
| 가상 GPS | `delivery-gps.mode=virtual`(기본)이면 픽업 직후 시작. 2초마다 경로를 따라 `경과 시간 × 속도`만큼 이동 |
| 도착 | 배달지 30m 안이면 `arrived=true`, 가상 GPS 는 그 자리에서 멈춤. **완료는 자동으로 하지 않는다**. 라이더가 `POST /{id}/complete` 를 직접 누른다 |

```
픽업 커밋 ──▶ PickupCompletedListener
               ├─ DeliveryRouteService.saveRoute()   카카오 길찾기 → route_path 저장 (실패 시 직선)
               └─ VirtualGpsSimulator.start()        Redis delivery:sim:{id} = 시작 시각, sim:active 에 추가
@Scheduled 2초 ──▶ VirtualGpsSimulator.tick()  ──▶ LocationReportService.report()  ──▶ Redis delivery:location:{id}
PUT /location ──▶ LocationReportService.reportReal()  (가상 GPS 중지 후 같은 report())
완료 커밋 ──▶ DeliveryFinishedListener ──▶ Redis 키 삭제
```

---

## 1. 준비

캐시 Redis 가 필요합니다. `compose.yaml` 의 `redis-cache` 는 아직 주석 처리돼 있습니다 (인프라 담당 확인 전). 그동안은 임시 컨테이너로 띄웁니다.

```bash
cd ~/baedalsak/baedalsak-server
docker compose up -d --wait postgres rabbitmq
set -a && . ./.env && set +a      # REDIS_CACHE_PASSWORD, KAKAO_REST_API_KEY 포함
docker run -d --rm --name baedalsak-tmp-redis-cache -p 6380:6379 redis:7-alpine \
  redis-server --requirepass "$REDIS_CACHE_PASSWORD" --maxmemory 64mb --maxmemory-policy allkeys-lru
```

delivery 를 실행합니다. 이동을 빨리 보려면 속도를 올립니다 (기본 20km/h, 1.2km 에 약 4분).

```bash
SPRING_PROFILES_ACTIVE=local DELIVERY_GPS_SPEED_KMH=120 ./gradlew :delivery:bootRun
```

헤더 (curl 은 gateway 를 거치지 않으므로 직접 넣는다):

```bash
RID='-H X-Member-Id:302 -H X-Member-Role:RIDER'
CUS='-H X-Member-Id:101 -H X-Member-Role:CUSTOMER'
OWN='-H X-Member-Id:102 -H X-Member-Role:OWNER'
REDIS() { docker exec -e REDISCLI_AUTH="$REDIS_CACHE_PASSWORD" baedalsak-tmp-redis-cache redis-cli "$@"; }
```

## 2. 배달 만들기 (OrderAccepted 발행)

order 를 띄우지 않고 RabbitMQ 관리 API 로 이벤트를 직접 보냅니다. 주소는 카카오에서 찾아지는 실제 주소를 씁니다.

```bash
python3 - > /tmp/pub.json <<'EOF'
import json, uuid
ev = {"orderId": 990011, "customerId": 101, "customerPhone": "010-1234-5678", "ownerId": 102, "storeId": 103,
      "storeName": "성수 떡볶이", "pickupAddress": "서울 성동구 성수동2가 289-5",
      "deliveryAddress": "서울 성동구 성수이로 77, 302호", "deliveryDong": "성수동2가", "deliveryFee": 3500,
      "riderRequest": "문 앞", "estimatedReadyAt": "2026-11-06T16:00:00+09:00",
      "customerEmail": "c@example.com", "ownerEmail": "o@example.com"}
print(json.dumps({"properties": {"message_id": str(uuid.uuid4()), "content_type": "application/json"},
                  "routing_key": "order.accepted", "payload": json.dumps(ev, ensure_ascii=False),
                  "payload_encoding": "string"}, ensure_ascii=False))
EOF
curl -s -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" -H 'content-type: application/json' \
  -X POST "http://localhost:15672/api/exchanges/%2F/baedalsak.events/publish" --data @/tmp/pub.json
```

로그에 `delivery coordinates filled: ... pickup=true, destination=true` 가 찍히면 좌표 변환 성공입니다. `deliveryId` 는 로그 `delivery created: deliveryId=N` 에서 확인합니다.

```bash
ID=1   # 로그의 deliveryId
curl -s $CUS localhost:8084/v1/deliveries/by-order/990011/location   # status=WAITING, location=null
curl -s -o /dev/null -w '%{http_code}\n' $CUS localhost:8084/v1/deliveries/by-order/990011/route   # 204 (픽업 전)
```

## 3. 수락 → 픽업 → 위치 이동

```bash
curl -s -X POST $RID localhost:8084/v1/deliveries/$ID/accept > /dev/null
curl -s -X POST $RID localhost:8084/v1/deliveries/$ID/pickup > /dev/null
curl -s $OWN localhost:8084/v1/deliveries/by-order/990011/route      # path 19점 · distanceM 1274 · straight=false
for i in 1 2 3 4 5; do curl -s $CUS localhost:8084/v1/deliveries/by-order/990011/location; echo; sleep 3; done
```

**기대 결과**

- `remainingDistanceM` 이 계속 줄어든다 (예: 1248 → 1112 → 1044 → 909 …)
- 가상 GPS 는 속도가 일정해 `etaAt` 이 거의 그대로다
- 끝에 닿으면 `remainingDistanceM=0`, `arrived=true`, `etaAt` = 도착 시각으로 고정. **status 는 `PICKED_UP` 그대로**
- `$CUS` · `$OWN` · `$RID` 셋이 같은 값을 받는다. 다른 회원(`X-Member-Id:999`)은 403

Redis 확인:

```bash
REDIS keys 'delivery:*'          # delivery:location:1, delivery:sim:1, delivery:sim:active
REDIS ttl delivery:location:$ID  # 약 1800 (쓸 때마다 다시 30분)
```

## 4. 실제 GPS 로 바꾸기

```bash
curl -s -X PUT $RID -H 'content-type: application/json' -d '{"lat":37.5447,"lng":127.0573}' \
  localhost:8084/v1/deliveries/$ID/location
REDIS smembers delivery:sim:active   # 이 배달이 빠짐 → 가상 GPS 가 더 이상 움직이지 않음
```

| 요청 | 결과 |
|---|---|
| 위도 91 같은 범위 밖 좌표 | 400 |
| 다른 라이더 | 403 `FORBIDDEN` |
| 픽업 전 · 완료 후 | 409 `DELIVERY_INVALID_STATUS` |

## 5. 완료 → 정리

```bash
curl -s -X POST $RID localhost:8084/v1/deliveries/$ID/complete > /dev/null
curl -s $CUS localhost:8084/v1/deliveries/by-order/990011/location   # status=DELIVERED, location=null
REDIS keys "delivery:*:$ID"                                         # 비어 있음
```

## 6. 정리

```bash
docker stop baedalsak-tmp-redis-cache
```

---

## 참고

- 카카오 키(`KAKAO_REST_API_KEY`)가 없으면 좌표 변환 · 길찾기를 건너뛴다. 좌표가 없으면 경로 · 위치도 없다.
- 길찾기가 실패하면 가게 → 배달지 직선 경로를 저장한다 (`straight=true`).
- 캐시 Redis 가 없거나 끊겨도 수락 · 픽업 · 완료 API 는 그대로 동작한다. 위치만 `null` 로 나간다 (health 의 Redis 검사는 꺼 둠).
- `DELIVERY_GPS_MODE=real` 이면 가상 GPS 가 시작되지 않고 `PUT /location` 으로만 위치가 들어온다.
