package com.baedalsak.member.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.member.domain.SmsLog;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {

  Optional<SmsLog> findByEventId(UUID eventId);

  /**
   * before 보다 오래된 행을 최대 limit 개 지우고, 지운 개수를 돌려준다 (#232).
   * 한 번에 다 지우면 잠금 · WAL 이 커지므로 호출한 쪽이 다 지울 때까지 나눠 부른다. 호출마다 트랜잭션 하나.
   */
  @Transactional
  @Modifying
  @Query(value = """
      DELETE FROM sms_log
       WHERE id IN (SELECT id FROM sms_log
                     WHERE created_at < :before
                     ORDER BY id
                     LIMIT :limit)
      """, nativeQuery = true)
  int deleteOlderThan(@Param("before") OffsetDateTime before, @Param("limit") int limit);
}
