package com.baedalsak.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 email.* 중 서비스가 읽는 값 (#207).
 *   verificationRequired: true(기본) 면 가입 때 이메일 인증 필수, false 면 건너뛰기 허용 (부하 테스트 날만)
 *   (sender · region · from 은 EmailSender · EmailConfig 가 직접 읽음)
 */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(boolean verificationRequired) {
}
