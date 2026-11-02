package com.baedalsak.delivery.mail;

import com.baedalsak.delivery.mail.MailTemplateRenderer.RenderedMail;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 배달 메일 템플릿 (#108). 리스너가 넘기는 변수로 렌더링되고 제목이 맞는지 확인
 */
class DeliveryMailTemplatesTest {

    final MailTemplateRenderer renderer = new MailTemplateRenderer();

    @Test
    void 배차완료_템플릿() {
        Map<String, String> data = Map.of("orderId", "1005", "riderName", "김라이더", "assignedAt", "2026-10-27 15:30");

        for (String template : new String[]{"delivery-assigned-customer", "delivery-assigned-owner"}) {
            RenderedMail mail = renderer.render(template, data);

            assertThat(mail.subject()).isEqualTo("[배달싹] 라이더가 배정되었어요");
            assertThat(mail.html()).contains("1005", "김라이더", "2026-10-27 15:30").doesNotContain("{{");
        }
    }

    @Test
    void 배달시작_템플릿() {
        RenderedMail mail = renderer.render("delivery-picked-up-customer",
                Map.of("orderId", "1005", "pickedUpAt", "2026-10-27 15:50"));

        assertThat(mail.subject()).isEqualTo("[배달싹] 배달이 시작되었어요");
        assertThat(mail.html()).contains("1005", "2026-10-27 15:50").doesNotContain("{{");
    }

    @Test
    void 배달완료_템플릿() {
        Map<String, String> data = Map.of("orderId", "1005", "completedAt", "2026-10-27 16:10");

        for (String template : new String[]{"delivery-completed-customer", "delivery-completed-owner"}) {
            RenderedMail mail = renderer.render(template, data);

            assertThat(mail.subject()).isEqualTo("[배달싹] 배달이 완료되었어요");
            assertThat(mail.html()).contains("1005", "2026-10-27 16:10").doesNotContain("{{");
        }
    }
}
