package com.baedalsak.payment.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;
import software.amazon.awssdk.services.sesv2.model.NotFoundException;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.TooManyRequestsException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * SES 템플릿 발송 (#37). 요청 구성과 영구 오류·일시 오류 구분
 */
@ExtendWith(MockitoExtension.class)
class SesMailSenderTest {

    @Mock
    SesV2Client sesClient;

    SesMailSender sesMailSender;

    @BeforeEach
    void setUp() {
        PaymentMailProperties properties = new PaymentMailProperties("ap-northeast-2", "no-reply@baedalsak.click",
                new PaymentMailProperties.Templates("customer-template", "owner-template"));
        sesMailSender = new SesMailSender(sesClient, properties, JsonMapper.builder().build());
    }

    @Test
    void 발신_주소_수신자_템플릿_변수로_요청한다() {
        sesMailSender.send("customer@example.com", "customer-template", Map.of("orderId", "31"));

        ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(request.capture());
        assertThat(request.getValue().fromEmailAddress()).isEqualTo("no-reply@baedalsak.click");
        assertThat(request.getValue().destination().toAddresses()).containsExactly("customer@example.com");
        assertThat(request.getValue().content().template().templateName()).isEqualTo("customer-template");
        assertThat(request.getValue().content().template().templateData()).isEqualTo("{\"orderId\":\"31\"}");
    }

    @Test
    void 거부된_메일은_영구_오류로_바꾼다() {
        given(sesClient.sendEmail(any(SendEmailRequest.class)))
                .willThrow(MessageRejectedException.builder().message("rejected").build());

        assertThatThrownBy(() -> sesMailSender.send("customer@example.com", "customer-template", Map.of()))
                .isInstanceOf(PermanentMailFailureException.class);
    }

    @Test
    void 템플릿이_없으면_영구_오류로_바꾼다() {
        given(sesClient.sendEmail(any(SendEmailRequest.class)))
                .willThrow(NotFoundException.builder().message("template").build());

        assertThatThrownBy(() -> sesMailSender.send("customer@example.com", "customer-template", Map.of()))
                .isInstanceOf(PermanentMailFailureException.class);
    }

    @Test
    void 쓰로틀링은_그대로_던져_재시도에_맡긴다() {
        given(sesClient.sendEmail(any(SendEmailRequest.class)))
                .willThrow(TooManyRequestsException.builder().message("slow down").build());

        assertThatThrownBy(() -> sesMailSender.send("customer@example.com", "customer-template", Map.of()))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
