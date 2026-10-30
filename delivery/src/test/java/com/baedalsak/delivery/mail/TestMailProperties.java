package com.baedalsak.delivery.mail;

// 테스트용 배달 메일 설정. 템플릿 이름은 '{메일}-{받는 사람}-template'
final class TestMailProperties {

    private TestMailProperties() {
    }

    static DeliveryMailProperties properties() {
        return new DeliveryMailProperties("ap-northeast-2", "no-reply@baedalsak.click",
                new DeliveryMailProperties.Templates("assigned-customer-template", "assigned-owner-template",
                        "picked-up-customer-template", "completed-customer-template", "completed-owner-template"));
    }
}
