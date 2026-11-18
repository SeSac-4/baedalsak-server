package com.baedalsak.member.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 sms-log.* (#232).
 *   retention: 이보다 오래된 sms_log 행을 정리 스케줄러가 지움
 *   (cleanup-cron 은 @Scheduled 가 직접 읽음)
 */
@ConfigurationProperties(prefix = "sms-log")
public record SmsLogProperties(Duration retention) {
}
