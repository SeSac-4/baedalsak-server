package com.baedalsak.payment.mail;

import com.baedalsak.payment.mail.MailTemplateRenderer.RenderedMail;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 메일 템플릿 렌더링 (#37). 실제 resources/mail 템플릿으로 값 채우기 · 메일 제목 · 이스케이프 · 누락 시 영구 오류
 */
class MailTemplateRendererTest {

    final MailTemplateRenderer renderer = new MailTemplateRenderer();

    @Test
    void 고객_템플릿에_값을_채우고_title_을_메일_제목으로_쓴다() {
        RenderedMail mail = renderer.render("payment-approved-customer", data());

        assertThat(mail.subject()).isEqualTo("[배달싹] 결제가 완료되었어요");
        assertThat(mail.html()).contains("1005", "싹분식", "18,000원", "2026-10-27 15:30")
                .doesNotContain("{{");
    }

    @Test
    void 사장님_템플릿은_새_주문_제목으로_보낸다() {
        RenderedMail mail = renderer.render("payment-approved-owner", data());

        assertThat(mail.subject()).isEqualTo("[배달싹] 새 주문이 들어왔어요");
        assertThat(mail.html()).contains("1005", "18,000원", "2026-10-27 15:30").doesNotContain("{{");
    }

    @Test
    void 값의_HTML_특수문자는_이스케이프한다() {
        Map<String, String> data = data();
        data.put("storeName", "<b>싹&분식</b>");

        RenderedMail mail = renderer.render("payment-approved-customer", data);

        assertThat(mail.html()).contains("&lt;b&gt;싹&amp;분식&lt;/b&gt;").doesNotContain("<b>싹");
    }

    @Test
    void 템플릿에_있는_변수가_없으면_영구_오류() {
        Map<String, String> data = data();
        data.remove("amount");

        assertThatThrownBy(() -> renderer.render("payment-approved-customer", data))
                .isInstanceOf(PermanentMailFailureException.class);
    }

    @Test
    void 템플릿_파일이_없으면_영구_오류() {
        assertThatThrownBy(() -> renderer.render("no-such-template", data()))
                .isInstanceOf(PermanentMailFailureException.class);
    }

    private static Map<String, String> data() {
        Map<String, String> data = new HashMap<>();
        data.put("orderId", "1005");
        data.put("storeName", "싹분식");
        data.put("amount", "18,000원");
        data.put("approvedAt", "2026-10-27 15:30");
        return data;
    }
}
