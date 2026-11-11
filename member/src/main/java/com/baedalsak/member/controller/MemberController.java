package com.baedalsak.member.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.member.dto.MemberMeResponse;
import com.baedalsak.member.service.MemberQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.baedalsak.member.dto.PhoneVerifyRequest;
import com.baedalsak.member.service.PhoneVerificationService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/** 회원 API (외부, gateway 경유: /api/v1/members/** → /v1/members/**). */
@RestController
@RequestMapping("/v1/members")
@RequiredArgsConstructor
public class MemberController {

  private final MemberQueryService memberQueryService;
  private final PhoneVerificationService phoneVerificationService;

  // 내 계정 정보 (API 명세 8.3.1). 헤더 없거나 잘못되면 401 — @CurrentMember 가 처리
  @GetMapping("/me")
  public MemberMeResponse me(@CurrentMember LoginMember me) {
    return memberQueryService.getMe(me.id());
  }

    // 내 번호로 인증번호 받기 (#164) — 202. 이미 인증 409 · 60초 간격 · 하루 5통 넘으면 429
  @PostMapping("/me/phone/code")
  public ResponseEntity<Void> sendPhoneCode(@CurrentMember LoginMember me) {
    phoneVerificationService.sendCode(me.id());
    return ResponseEntity.accepted().build();
  }

  // 인증번호 확인 (#164) — 204. 틀림·만료 400 errors[code] · 5번 틀리면 429
  @PostMapping("/me/phone/verify")
  public ResponseEntity<Void> verifyPhone(@CurrentMember LoginMember me,
      @Valid @RequestBody PhoneVerifyRequest req) {
    phoneVerificationService.verify(me.id(), req.code());
    return ResponseEntity.noContent().build();
  }
}