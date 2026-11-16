package com.baedalsak.delivery.location;

/**
 * 서비스 안 이벤트: 배달이 완료됐다 (RabbitMQ 로 나가는 DeliveryCompleted 와 별개). 커밋 후 Redis 정리에 쓴다.
 *
 * @param deliveryId    완료된 배달 ID
 * @param riderId       배달한 라이더 ID
 * @param deliveryPoint 배달지 좌표 (라이더를 이 자리에 남긴다). 주소 변환 실패면 null
 */
public record DeliveryFinishedEvent(Long deliveryId, Long riderId, GeoPoint deliveryPoint) {
}
