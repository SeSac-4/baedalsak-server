package com.baedalsak.payment.mail;

import com.baedalsak.payment.domain.MailLog;
import com.baedalsak.payment.domain.MailLogStatus;
import com.baedalsak.payment.repository.MailLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * mail_log 선점 (#37, 설계서 '상태 전이' mail_log.status). 새 행·SENDING 은 보내고 SENT 는 건너뜀
 */
@ExtendWith(MockitoExtension.class)
class MailLogWriterTest {

    static final UUID EVENT_ID = UUID.fromString("0b6f2a4e-3c1d-4f5a-9e8b-7d6c5b4a3f21");
    static final String RECIPIENT = "customer@example.com";

    @Mock
    MailLogRepository mailLogRepository;
    @InjectMocks
    MailLogWriter mailLogWriter;

    @Test
    void 처음_받은_이벤트면_선점하고_보낸다() {
        given(mailLogRepository.insertSending(EVENT_ID, RECIPIENT)).willReturn(1);

        assertThat(mailLogWriter.claim(EVENT_ID, RECIPIENT)).isTrue();
    }

    @Test
    void SENDING_으로_남아_있으면_다시_보낸다() {
        given(mailLogRepository.insertSending(EVENT_ID, RECIPIENT)).willReturn(0);
        given(mailLogRepository.findByEventIdAndRecipient(EVENT_ID, RECIPIENT))
                .willReturn(Optional.of(mailLog(MailLogStatus.SENDING)));

        assertThat(mailLogWriter.claim(EVENT_ID, RECIPIENT)).isTrue();
    }

    @Test
    void 이미_SENT_면_보내지_않는다() {
        given(mailLogRepository.insertSending(EVENT_ID, RECIPIENT)).willReturn(0);
        given(mailLogRepository.findByEventIdAndRecipient(EVENT_ID, RECIPIENT))
                .willReturn(Optional.of(mailLog(MailLogStatus.SENT)));

        assertThat(mailLogWriter.claim(EVENT_ID, RECIPIENT)).isFalse();
    }

    @Test
    void 발송_후_SENT_로_바꾼다() {
        mailLogWriter.markSent(EVENT_ID, RECIPIENT);

        verify(mailLogRepository).markSent(eq(EVENT_ID), eq(RECIPIENT), any(), eq(MailLogStatus.SENT));
    }

    private static MailLog mailLog(MailLogStatus status) {
        MailLog mailLog = BeanUtils.instantiateClass(MailLog.class);
        ReflectionTestUtils.setField(mailLog, "status", status);
        return mailLog;
    }
}
