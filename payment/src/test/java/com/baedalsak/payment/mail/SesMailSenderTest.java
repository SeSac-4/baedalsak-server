package com.baedalsak.payment.mail;

import com.baedalsak.payment.mail.MailTemplateRenderer.RenderedMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.TooManyRequestsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SES 발송 (#37). 앱이 렌더링한 제목 · HTML 을 그대로 보내는지, 렌더링 실패 · 영구 오류 · 일시 오류 구분
 */
@ExtendWith(MockitoExtension.class)
class SesMailSenderTest {

    static final Map<String, String> DATA = Map.of("orderId", "31");

    @Mock
    SesV2Client sesClient;
    @Mock
    MailTemplateRenderer renderer;

    SesMailSender sesMailSender;

    @BeforeEach
    void setUp() {
        PaymentMailProperties properties = new PaymentMailProperties("ap-northeast-2", "no-reply@baedalsak.click",
                new PaymentMailProperties.Templates("customer-template", "owner-template"));
        sesMailSender = new SesMailSender(sesClient, properties, renderer);
    }

    @Test
    void 렌더링한_제목과_HTML_을_발신_주소_수신자와_함께_보낸다() {
        given(renderer.render("customer-template", DATA))
                .willReturn(new RenderedMail("[배달싹] 결제가 완료되었어요", "<html>31</html>"));

        sesMailSender.send("customer@example.com", "customer-template", DATA);

        ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(request.capture());
        assertThat(request.getValue().fromEmailAddress()).isEqualTo("no-reply@baedalsak.click");
        assertThat(request.getValue().destination().toAddresses()).containsExactly("customer@example.com");
        assertThat(request.getValue().content().template()).isNull();
        assertThat(request.getValue().content().simple().subject().data()).isEqualTo("[배달싹] 결제가 완료되었어요");
        assertThat(request.getValue().content().simple().subject().charset()).isEqualTo("UTF-8");
        assertThat(request.getValue().content().simple().body().html().data()).isEqualTo("<html>31</html>");
    }

    @Test
    void 렌더링이_실패하면_SES_를_부르지_않는다() {
        willThrow(new PermanentMailFailureException("missing variable", null))
                .given(renderer).render("customer-template", DATA);

        assertThatThrownBy(() -> sesMailSender.send("customer@example.com", "customer-template", DATA))
                .isInstanceOf(PermanentMailFailureException.class);

        verifyNoInteractions(sesClient);
    }

    @Test
    void 거부된_메일은_영구_오류로_바꾼다() {
        given(renderer.render("customer-template", DATA)).willReturn(new RenderedMail("제목", "<html></html>"));
        given(sesClient.sendEmail(any(SendEmailRequest.class)))
                .willThrow(MessageRejectedException.builder().message("rejected").build());

        assertThatThrownBy(() -> sesMailSender.send("customer@example.com", "customer-template", DATA))
                .isInstanceOf(PermanentMailFailureException.class);
    }

    @Test
    void 쓰로틀링은_그대로_던져_재시도에_맡긴다() {
        given(renderer.render("customer-template", DATA)).willReturn(new RenderedMail("제목", "<html></html>"));
        given(sesClient.sendEmail(any(SendEmailRequest.class)))
                .willThrow(TooManyRequestsException.builder().message("slow down").build());

        assertThatThrownBy(() -> sesMailSender.send("customer@example.com", "customer-template", DATA))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
