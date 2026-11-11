package com.baedalsak.member.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.config.SmsProperties;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.Role;
import com.baedalsak.member.repository.MemberRepository;
import com.baedalsak.member.sms.SmsSender;

/** 알림 문자 규칙 (#182): DB · 문자는 가짜(mock)로 두고 보낼지 말지만 검사 */
class SmsNotificationServiceTest {

  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final String TYPE = DeliveryPickedUpEvent.TYPE;
  private static final String MESSAGE = "[배달싹] 라이더가 음식을 픽업해 배달을 시작했어요";

  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private final SmsLogWriter smsLogWriter = mock(SmsLogWriter.class);
  private final SmsSender smsSender = mock(SmsSender.class);

  private Member member;

  @BeforeEach
  void setUp() {
    member = new Member("a@test.com", "고객", "010-1234-5678", Role.CUSTOMER);
    when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
    when(smsLogWriter.claim(EVENT_ID, TYPE, 1L)).thenReturn(true);
  }

  private SmsNotificationService service(boolean enabled) {
    return new SmsNotificationService(new SmsProperties(false, enabled), memberRepository, smsLogWriter, smsSender);
  }

  @Test
  void 인증한_고객이면_DB_번호로_보내고_SENT_기록() {
    member.markPhoneVerified();

    service(true).send(EVENT_ID, TYPE, 1L, MESSAGE);

    verify(smsSender).send("010-1234-5678", MESSAGE);
    verify(smsLogWriter).markSent(EVENT_ID);
  }

  @Test
  void 인증_안_한_고객이면_안_보내고_기록도_안_남김() {
    service(true).send(EVENT_ID, TYPE, 1L, MESSAGE);

    verify(smsLogWriter, never()).claim(any(), anyString(), anyLong());
    verify(smsSender, never()).send(anyString(), anyString());
  }

  @Test
  void 스위치가_꺼져_있으면_아무것도_안_함() {
    member.markPhoneVerified();

    service(false).send(EVENT_ID, TYPE, 1L, MESSAGE);

    verifyNoInteractions(memberRepository, smsLogWriter, smsSender);
  }

  @Test
  void 없는_회원이면_조용히_끝냄() {
    service(true).send(EVENT_ID, TYPE, 999L, MESSAGE);

    verify(smsSender, never()).send(anyString(), anyString());
  }

  @Test
  void 이미_보낸_이벤트면_다시_안_보냄() {
    member.markPhoneVerified();
    when(smsLogWriter.claim(EVENT_ID, TYPE, 1L)).thenReturn(false);

    service(true).send(EVENT_ID, TYPE, 1L, MESSAGE);

    verify(smsSender, never()).send(anyString(), anyString());
  }

  @Test
  void 문자_실패면_예외를_그대로_던지고_SENT_안_남김() {   // → 재시도 2번 → DLQ
    member.markPhoneVerified();
    doThrow(new BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE))
        .when(smsSender).send(anyString(), anyString());

    assertThatThrownBy(() -> service(true).send(EVENT_ID, TYPE, 1L, MESSAGE))
        .isInstanceOf(BusinessException.class);
    verify(smsLogWriter, never()).markSent(any());
  }

  @Test
  void customerId_가_없으면_재시도_없이_DLQ() {
    assertThatThrownBy(() -> service(true).send(EVENT_ID, TYPE, null, MESSAGE))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);
  }

  @Test
  void messageId_가_없거나_UUID_가_아니면_재시도_없이_DLQ() {
    assertThatThrownBy(() -> SmsNotificationService.parseEventId(TYPE, null))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    assertThatThrownBy(() -> SmsNotificationService.parseEventId(TYPE, "abc"))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    assertThat(SmsNotificationService.parseEventId(TYPE, EVENT_ID.toString())).isEqualTo(EVENT_ID);
  }
}