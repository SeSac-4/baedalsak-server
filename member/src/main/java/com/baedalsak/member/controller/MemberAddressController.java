package com.baedalsak.member.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.member.dto.AddressRequest;
import com.baedalsak.member.dto.AddressResponse;
import com.baedalsak.member.service.MemberAddressService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/** 고객 배달 주소록 (#215, gateway 경유: /api/v1/members/me/addresses → /v1/members/me/addresses). 고객만 */
@RestController
@RequestMapping("/v1/members/me/addresses")
@RequiredArgsConstructor
public class MemberAddressController {

  private final MemberAddressService memberAddressService;

  // 내 주소 목록 — 200 (기본 주소 먼저)
  @GetMapping
  public List<AddressResponse> getAddresses(@CurrentMember LoginMember me) {
    return memberAddressService.getAddresses(me);
  }

  // 주소 추가 — 201. 3개면 409 ADDRESS_LIMIT_EXCEEDED
  @PostMapping
  public ResponseEntity<AddressResponse> addAddress(@CurrentMember LoginMember me,
      @Valid @RequestBody AddressRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(memberAddressService.addAddress(me, req));
  }

  // 주소 수정 · 기본 주소 지정 — 204. 없거나 남의 주소 404
  @PutMapping("/{addressId}")
  public ResponseEntity<Void> updateAddress(@CurrentMember LoginMember me, @PathVariable long addressId,
      @Valid @RequestBody AddressRequest req) {
    memberAddressService.updateAddress(me, addressId, req);
    return ResponseEntity.noContent().build();
  }

  // 주소 삭제 — 204. 기본 주소를 지우면 가장 최근 주소가 기본
  @DeleteMapping("/{addressId}")
  public ResponseEntity<Void> removeAddress(@CurrentMember LoginMember me, @PathVariable long addressId) {
    memberAddressService.removeAddress(me, addressId);
    return ResponseEntity.noContent().build();
  }
}
