package com.baedalsak.member.dto;

/** 가입 전 이메일 인증 성공 — 10분 안에 가입 요청의 emailVerificationToken 으로 보낸다 (1회용) */
public record EmailVerifyResponse(String emailVerificationToken) {

  @Override
  public String toString() {
    return "EmailVerifyResponse[emailVerificationToken=****]";
  }
}
