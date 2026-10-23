package com.baedalsak.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.dto.MemberInternalResponse;
import com.baedalsak.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** 회원 조회 (쓰기 없음). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService {

  private final MemberRepository memberRepository;

  // [내부] order·delivery 가 호출. 없으면 404 NOT_FOUND (회원은 세부 코드 없음)
  public MemberInternalResponse getInternal(Long memberId) {
    return MemberInternalResponse.from(findMember(memberId));
  }

  // 내 정보 조회(/members/me)에서도 같이 쓸 예정이라 따로 뺌
  private Member findMember(Long memberId) {
    return memberRepository.findById(memberId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
  }
}