package com.baedalsak.payment.mail;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;

/**
 * 메일 템플릿 렌더링 (#37). resources/mail/{이름}.html 의 {{변수}} 를 채우고, &lt;title&gt; 을 메일 제목으로 쓴다.
 * SES 는 완성된 제목·본문만 받는다 — 렌더링이 SES 호출 전에 끝나서, 실패하면 mail_log 가 SENT 가 되지 않는다.
 * jmustache 기본값: 없는 변수는 예외, 값은 HTML 이스케이프. 다시 해도 같은 결과라 영구 오류(바로 DLQ)로 바꾼다.
 */
@Component
public class MailTemplateRenderer {

  private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

  private final Mustache.Compiler compiler = Mustache.compiler();
  private final Map<String, Template> templates = new ConcurrentHashMap<>();

  public RenderedMail render(String templateName, Map<String, String> data) {
    Template template = templates.computeIfAbsent(templateName, this::compile);
    String html;
    try {
      html = template.execute(data);
    } catch (MustacheException e) {
      throw new PermanentMailFailureException("메일 템플릿 렌더링 실패 template=" + templateName, e);
    }
    Matcher title = TITLE.matcher(html);
    if (!title.find() || title.group(1).isBlank()) {
      throw new PermanentMailFailureException("메일 템플릿에 <title>(메일 제목) 없음 template=" + templateName, null);
    }
    return new RenderedMail(HtmlUtils.htmlUnescape(title.group(1).strip()), html);
  }

  // 템플릿 파일은 바뀌지 않으므로 처음 한 번만 읽어 컴파일해 둔다 (실패하면 저장하지 않음)
  private Template compile(String templateName) {
    ClassPathResource resource = new ClassPathResource("mail/" + templateName + ".html");
    try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
      return compiler.compile(reader);
    } catch (IOException | MustacheException e) {
      throw new PermanentMailFailureException("메일 템플릿을 읽을 수 없음 template=" + templateName, e);
    }
  }

  public record RenderedMail(String subject, String html) {
  }
}
