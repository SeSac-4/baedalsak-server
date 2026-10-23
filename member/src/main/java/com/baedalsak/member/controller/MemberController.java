package com.baedalsak.member.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.member.dto.MemberMeResponse;
import com.baedalsak.member.service.MemberQueryService;

import lombok.RequiredArgsConstructor;

/** 회원 API (외부, gateway 경유: /api/v1/members/** → /v1/members/**). */
@RestController
@RequestMapping("/v1/members")
@RequiredArgsConstructor
public class MemberController {

  private final MemberQueryService memberQueryService;

  // 내 계정 정보 (API 명세 8.3.1). 헤더 없거나 잘못되면 401 — @CurrentMember 가 처리
  @GetMapping("/me")
  public MemberMeResponse me(@CurrentMember LoginMember me) {
    return memberQueryService.getMe(me.id());
  }
}