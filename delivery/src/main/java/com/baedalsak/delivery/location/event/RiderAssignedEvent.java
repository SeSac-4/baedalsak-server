package com.baedalsak.delivery.location.event;

/**
 * 서비스 안 이벤트: 배차가 수락됐다 (RabbitMQ 로 나가는 DeliveryAssigned 와 별개). 커밋 후 가게까지 경로 · 가상 GPS 시작에 쓴다.
 *
 * @param deliveryId 배차된 배달 ID
 */
public record RiderAssignedEvent(Long deliveryId) {
}
