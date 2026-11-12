package com.baedalsak.delivery.location;

/**
 * 서비스 안 이벤트: 라이더가 배차를 수락했다 (RabbitMQ 로 나가는 DeliveryAssigned 와 별개). 커밋 후 가게까지 경로 저장에 쓴다.
 *
 * @param deliveryId 배차된 배달 ID
 */
public record RiderAssignedEvent(Long deliveryId) {
}
