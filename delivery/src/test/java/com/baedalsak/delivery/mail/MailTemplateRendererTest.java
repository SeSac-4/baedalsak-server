package com.baedalsak.delivery.mail;

import com.baedalsak.delivery.mail.MailTemplateRenderer.RenderedMail;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 메일 템플릿 렌더링 (#108). 값 채우기 · title 을 메일 제목으로 · 이스케이프 · 누락 시 영구 오류 (테스트용 템플릿)
 */
class MailTemplateRendererTest {

    final MailTemplateRenderer renderer = new MailTemplateRenderer();

    @Test
    void 값을_채우고_title_을_메일_제목으로_쓴다() {
        RenderedMail mail = renderer.render("test-template", data());

        assertThat(mail.subject()).isEqualTo("[배달싹] 테스트 & 제목");
        assertThat(mail.html()).contains("1005", "김싹").doesNotContain("{{");
    }

    @Test
    void 값의_HTML_특수문자는_이스케이프한다() {
        Map<String, String> data = data();
        data.put("name", "<b>김&싹</b>");

        RenderedMail mail = renderer.render("test-template", data);

        assertThat(mail.html()).contains("&lt;b&gt;김&amp;싹&lt;/b&gt;").doesNotContain("<b>김");
    }

    @Test
    void 템플릿에_있는_변수가_없으면_영구_오류() {
        Map<String, String> data = data();
        data.remove("name");

        assertThatThrownBy(() -> renderer.render("test-template", data))
                .isInstanceOf(PermanentMailFailureException.class);
    }

    @Test
    void title_이_없으면_영구_오류() {
        assertThatThrownBy(() -> renderer.render("no-title-template", data()))
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
        data.put("name", "김싹");
        return data;
    }
}
