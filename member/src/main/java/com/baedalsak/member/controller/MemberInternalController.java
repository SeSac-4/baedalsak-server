package com.baedalsak.member.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.member.dto.MemberInternalResponse;
import com.baedalsak.member.service.MemberQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 서비스 간 내부 호출 전용 (order·delivery → member).
 * gateway 에 /internal/** 라우트가 없어서 외부에서는 404 → 인증 헤더 검사를 하지 않는다.
 */
@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class MemberInternalController {

  private final MemberQueryService memberQueryService;

  @GetMapping("/{memberId}")
  public MemberInternalResponse getMember(@PathVariable Long memberId) {
    return memberQueryService.getInternal(memberId);
  }
}