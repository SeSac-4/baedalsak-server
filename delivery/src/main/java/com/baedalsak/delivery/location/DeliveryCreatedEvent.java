package com.baedalsak.delivery.location;

/**
 * 서비스 안 이벤트: 배달 요청이 생성됐다 (RabbitMQ 로 나가지 않음). 커밋 후 좌표 변환에 쓴다.
 *
 * @param deliveryId 생성된 배달 ID
 */
public record DeliveryCreatedEvent(Long deliveryId) {
}
