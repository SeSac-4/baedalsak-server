package com.baedalsak.delivery.location;

/**
 * 서비스 안 이벤트: 배달이 완료됐다 (RabbitMQ 로 나가는 DeliveryCompleted 와 별개). 커밋 후 Redis 정리에 쓴다.
 *
 * @param deliveryId 완료된 배달 ID
 */
public record DeliveryFinishedEvent(Long deliveryId) {
}
