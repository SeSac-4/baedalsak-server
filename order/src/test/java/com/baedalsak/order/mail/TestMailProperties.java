package com.baedalsak.order.mail;

// 테스트용 주문 메일 설정. 템플릿 이름은 '{메일}-{받는 사람}-template'
final class TestMailProperties {

    private TestMailProperties() {
    }

    static OrderMailProperties properties() {
        return new OrderMailProperties("ap-northeast-2", "no-reply@baedalsak.click",
                new OrderMailProperties.Templates("accepted-customer-template"));
    }
}
