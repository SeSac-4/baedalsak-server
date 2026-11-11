package com.baedalsak.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 sms.* 중 서비스가 읽는 값 (#164).
 *   verificationRequired: true 면 가입 때 휴대폰 인증 필수, false 면 건너뛰기 허용 (SMS 샌드박스 동안 false)
 *   (sender · region 은 SmsSender · SmsConfig 가 직접 읽음)
 */
@ConfigurationProperties(prefix = "sms")
public record SmsProperties(boolean verificationRequired) {
}
