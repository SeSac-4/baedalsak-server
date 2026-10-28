package com.baedalsak.delivery.service;

import com.baedalsak.delivery.client.MemberClient;
import com.baedalsak.delivery.domain.Delivery;
import com.baedalsak.delivery.domain.DeliveryStatus;
import com.baedalsak.delivery.domain.Outbox;
import com.baedalsak.delivery.dto.RiderDeliveryResponse;
import com.baedalsak.delivery.exception.BusinessException;
import com.baedalsak.delivery.exception.ErrorCode;
import com.baedalsak.delivery.payload.DeliveryAssignedEvent;
import com.baedalsak.delivery.repository.DeliveryRepository;
import com.baedalsak.delivery.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

/**
 * 6.2.2 배차 수락. 라이더가 WAITING 배달을 수락해 ASSIGNED 로 배정받는다.
 * 여러 라이더가 동시에 수락하면 조건부 UPDATE 로 1명만 성공하고, 나머지는 409 DELIVERY_ALREADY_ASSIGNED (N2.5).
 */
@Slf4j
@Service
public class DeliveryAcceptService {

    private final DeliveryRepository deliveryRepository;
    private final OutboxRepository outboxRepository;
    private final MemberClient memberClient;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    public DeliveryAcceptService(DeliveryRepository deliveryRepository, OutboxRepository outboxRepository,
                                 MemberClient memberClient, JsonMapper jsonMapper,
                                 PlatformTransactionManager transactionManager) {
        this.deliveryRepository = deliveryRepository;
        this.outboxRepository = outboxRepository;
        this.memberClient = memberClient;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * member 호출은 트랜잭션 밖에서 먼저 한다 → HTTP 대기 동안 DB 커넥션·행 잠금을 잡고 있지 않는다.
     * 실패하면 riderName 을 비운 채 배차는 성공시킨다 (설계서 '동기 호출' 탭).
     */
    public RiderDeliveryResponse accept(Long deliveryId, Long riderId) {
        String riderName = memberClient.findName(riderId).orElse(null);
        return transactionTemplate.execute(status -> assign(deliveryId, riderId, riderName));
    }

    // 배달 UPDATE 와 outbox INSERT 를 한 트랜잭션에 → 배정됐는데 이벤트가 빠지는 일이 없다 (docs 3-2)
    private RiderDeliveryResponse assign(Long deliveryId, Long riderId, String riderName) {
        // 라이더는 진행 중 배달을 동시에 1건만 (API 명세 6.2.2 비고). 같은 배달을 두 번 누른 경우도 여기서 걸린다
        if (deliveryRepository.existsByRiderIdAndStatusIn(riderId, DeliveryStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.DELIVERY_INVALID_STATUS);
        }

        if (deliveryRepository.assignIfWaiting(deliveryId, riderId, riderName, OffsetDateTime.now()) == 0) {
            // 0행: 없는 배달이거나, 다른 라이더가 먼저 배정받음
            if (!deliveryRepository.existsById(deliveryId)) {
                throw new BusinessException(ErrorCode.DELIVERY_NOT_FOUND);
            }
            log.info("delivery already assigned: deliveryId={}, riderId={}", deliveryId, riderId);
            throw new BusinessException(ErrorCode.DELIVERY_ALREADY_ASSIGNED);
        }

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        outboxRepository.save(Outbox.of(
                DeliveryAssignedEvent.TYPE,
                DeliveryAssignedEvent.ROUTING_KEY,
                delivery.getId(),
                jsonMapper.writeValueAsString(DeliveryAssignedEvent.from(delivery)),
                MDC.get("traceId")));

        log.info("delivery assigned: deliveryId={}, orderId={}, riderId={}",
                delivery.getId(), delivery.getOrderId(), riderId);
        return RiderDeliveryResponse.from(delivery);
    }
}
