package com.baedalsak.store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 사진 업로드 URL(presigned PUT) 서명기 (#110). 서명만 계산하므로 S3 를 호출하지 않는다.
 * 자격 증명은 SDK 기본 체인이 찾는다 (로컬: ~/.aws 의 IAM 키 / AWS: Pod Identity).
 */
@Configuration
public class S3Config {

    @Bean
    S3Presigner s3Presigner(ImageProperties props) {
        return S3Presigner.builder()
                .region(Region.of(props.region()))
                .build();
    }
}
