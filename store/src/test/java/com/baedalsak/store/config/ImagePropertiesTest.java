package com.baedalsak.store.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사진 URL 조합 (#110). 인프라 IMAGE_BASE_URL 은 끝에 / 가 없다
 */
class ImagePropertiesTest {

    @Test
    void 기본_주소_끝의_슬래시와_관계없이_URL을_만든다() {
        assertThat(props("https://baedalsak.click/images").urlOf("stores/1/a.jpg"))
                .isEqualTo("https://baedalsak.click/images/stores/1/a.jpg");
        assertThat(props("https://baedalsak.click/images/").urlOf("stores/1/a.jpg"))
                .isEqualTo("https://baedalsak.click/images/stores/1/a.jpg");
    }

    @Test
    void 키가_없으면_null() {
        assertThat(props("https://baedalsak.click/images").urlOf(null)).isNull();
    }

    private ImageProperties props(String baseUrl) {
        return new ImageProperties("ap-northeast-2", "bucket", "images/", baseUrl, Duration.ofMinutes(5));
    }
}
