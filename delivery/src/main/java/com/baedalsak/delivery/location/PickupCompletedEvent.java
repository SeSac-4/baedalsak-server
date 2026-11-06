package com.baedalsak.delivery.location;

/**
 * 서비스 안 이벤트: 픽업이 완료됐다 (RabbitMQ 로 나가는 DeliveryPickedUp 과 별개). 커밋 후 경로 저장에 쓴다.
 *
 * @param deliveryId 픽업된 배달 ID
 */
public record PickupCompletedEvent(Long deliveryId) {
}
