package com.baedalsak.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.common.auth.MemberRole;
import com.baedalsak.common.error.BusinessException;
import com.baedalsak.common.error.CommonErrorCode;
import com.baedalsak.member.domain.MemberAddress;
import com.baedalsak.member.dto.AddressRequest;
import com.baedalsak.member.dto.AddressResponse;
import com.baedalsak.member.error.MemberErrorCode;
import com.baedalsak.member.repository.MemberAddressRepository;
import com.baedalsak.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 고객 배달 주소록 (#215). 고객만 · 최대 3개 · 기본 주소 항상 1개.
 * 바꾸는 요청은 회원 행을 잠그고 시작해 같은 회원의 동시 요청이 규칙을 깨지 않게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberAddressService {

  static final int MAX_ADDRESSES = 3;

  private final MemberAddressRepository addressRepository;
  private final MemberRepository memberRepository;

  /** 내 주소 목록 (기본 주소 먼저) */
  @Transactional(readOnly = true)
  public List<AddressResponse> getAddresses(LoginMember me) {
    requireCustomer(me);
    return addressRepository.findByMemberIdOrderByDefaultAddressDescIdDesc(me.id()).stream()
        .map(AddressResponse::from)
        .toList();
  }

  /** 주소 추가. 3개면 409. 첫 주소이거나 isDefault=true 면 기본 주소로 */
  public AddressResponse addAddress(LoginMember me, AddressRequest req) {
    requireCustomer(me);
    lockMember(me.id());
    List<MemberAddress> addresses = myAddresses(me.id());
    if (addresses.size() >= MAX_ADDRESSES) {
      throw new BusinessException(MemberErrorCode.ADDRESS_LIMIT_EXCEEDED);
    }
    boolean makeDefault = addresses.isEmpty() || Boolean.TRUE.equals(req.isDefault());
    if (makeDefault) {
      addresses.forEach(MemberAddress::clearDefault);   // 기본 주소는 하나만
    }
    MemberAddress saved = addressRepository.save(newAddress(me.id(), req, makeDefault));
    return AddressResponse.from(saved);
  }

  /** 주소 수정. isDefault=true 면 이 주소를 기본으로 (false 는 무시 — 기본 주소가 0개가 되지 않게) */
  public void updateAddress(LoginMember me, long addressId, AddressRequest req) {
    requireCustomer(me);
    lockMember(me.id());
    MemberAddress target = findMine(me.id(), addressId);
    target.update(req.label(), req.address(), req.addressDetail(), req.dong(), req.lat(), req.lng());
    if (Boolean.TRUE.equals(req.isDefault()) && !target.isDefaultAddress()) {
      myAddresses(me.id()).forEach(MemberAddress::clearDefault);
      target.makeDefault();
    }
  }

  /** 주소 삭제. 기본 주소를 지우면 남은 것 중 가장 최근 주소가 기본 */
  public void removeAddress(LoginMember me, long addressId) {
    requireCustomer(me);
    lockMember(me.id());
    MemberAddress target = findMine(me.id(), addressId);
    addressRepository.delete(target);
    if (target.isDefaultAddress()) {
      myAddresses(me.id()).stream()
          .filter(a -> !a.getId().equals(target.getId()))
          .findFirst()   // 목록이 id 큰(최근) 순
          .ifPresent(MemberAddress::makeDefault);
    }
  }

  /** 고객 가입 (SignupService) 때 받은 주소 — 첫 주소라 기본. 가입 트랜잭션 안에서 불린다 */
  public void addSignupAddress(long memberId, AddressRequest req) {
    addressRepository.save(newAddress(memberId, req, true));
  }

  private void requireCustomer(LoginMember me) {
    if (me.role() != MemberRole.CUSTOMER) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);   // 사장님 · 라이더는 배달 주소가 필요 없음
    }
  }

  private void lockMember(long memberId) {
    memberRepository.findByIdForUpdate(memberId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));   // 탈퇴한 회원
  }

  private List<MemberAddress> myAddresses(long memberId) {
    return addressRepository.findByMemberIdOrderByDefaultAddressDescIdDesc(memberId);
  }

  private MemberAddress findMine(long memberId, long addressId) {
    return addressRepository.findByIdAndMemberId(addressId, memberId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));   // 없거나 남의 주소
  }

  private static MemberAddress newAddress(long memberId, AddressRequest req, boolean isDefault) {
    return new MemberAddress(memberId, req.label(), req.address(), req.addressDetail(),
        req.dong(), req.lat(), req.lng(), isDefault);
  }
}
