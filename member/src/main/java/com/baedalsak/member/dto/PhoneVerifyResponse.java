package com.baedalsak.member.dto;

/** 가입 전 인증 성공 — 10분 안에 가입 요청의 phoneVerificationToken 으로 보낸다 (1회용) */
public record PhoneVerifyResponse(String phoneVerificationToken) {

  @Override
  public String toString() {
    return "PhoneVerifyResponse[phoneVerificationToken=****]";
  }
}
