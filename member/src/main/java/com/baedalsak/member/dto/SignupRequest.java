package com.baedalsak.member.dto;

/**
 * 가입 3종(고객·사장님·라이더) 공통 입력. 역할은 본문이 아니라 경로로 정한다 (API 명세 8.1.1~8.1.3).
 * 가입 서비스는 역할과 관계없이 이 4개로 member 를 만든다.
 */
public interface SignupRequest {
  String email();
  String password();
  String name();
  String phone();
}