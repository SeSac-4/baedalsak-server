package com.baedalsak.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.OwnerProfile;
import com.baedalsak.member.domain.RiderProfile;
import com.baedalsak.member.dto.MemberInternalResponse;
import com.baedalsak.member.dto.MemberMeResponse;
import com.baedalsak.member.repository.MemberRepository;
import com.baedalsak.member.repository.OwnerProfileRepository;
import com.baedalsak.member.repository.RiderProfileRepository;

import lombok.RequiredArgsConstructor;

/** 회원 조회 (쓰기 없음). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService {

  private final MemberRepository memberRepository;
  private final OwnerProfileRepository ownerProfileRepository;   // 추가
  private final RiderProfileRepository riderProfileRepository;   // 추가

  // [내부] order·delivery 가 호출. 없으면 404 NOT_FOUND (회원은 세부 코드 없음)
  public MemberInternalResponse getInternal(Long memberId) {
    return MemberInternalResponse.from(findMember(memberId));
  }

  // 내 계정 정보. 역할에 맞는 프로필 값만 채우고 나머지는 null (응답에서 빠짐)
  public MemberMeResponse getMe(Long memberId) {
    Member member = findMember(memberId);

    String storeNameAtSignup = null;
    String vehicleType = null;
    switch (member.getRole()) {
      case OWNER -> storeNameAtSignup = ownerProfileRepository.findById(memberId)
          .map(OwnerProfile::getStoreNameAtSignup)
          .orElse(null);
      case RIDER -> vehicleType = riderProfileRepository.findById(memberId)
          .map(RiderProfile::getVehicleType)
          .orElse(null);
      case CUSTOMER -> { }   // 고객은 프로필 없음
    }
    return MemberMeResponse.of(member, storeNameAtSignup, vehicleType);
  }

  private Member findMember(Long memberId) {
    return memberRepository.findById(memberId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
  }
}