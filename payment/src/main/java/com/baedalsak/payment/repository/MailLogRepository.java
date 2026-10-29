package com.baedalsak.payment.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baedalsak.payment.domain.MailLog;
import com.baedalsak.payment.domain.MailLogStatus;

public interface MailLogRepository extends JpaRepository<MailLog, Long> {

  // 선점: (event_id, recipient) 행을 SENDING 으로 만든다. 이미 있으면 아무것도 하지 않고 0 을 돌려준다
  @Modifying
  @Query(value = """
      INSERT INTO mail_log (event_id, recipient) VALUES (:eventId, :recipient)
      ON CONFLICT (event_id, recipient) DO NOTHING
      """, nativeQuery = true)
  int insertSending(@Param("eventId") UUID eventId, @Param("recipient") String recipient);

  Optional<MailLog> findByEventIdAndRecipient(UUID eventId, String recipient);

  // SES 발송 성공 후 SENT 로 바꾼다
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE MailLog m SET m.status = :sent, m.sentAt = :sentAt
       WHERE m.eventId = :eventId AND m.recipient = :recipient
      """)
  int markSent(@Param("eventId") UUID eventId, @Param("recipient") String recipient,
      @Param("sentAt") OffsetDateTime sentAt, @Param("sent") MailLogStatus sent);
}
