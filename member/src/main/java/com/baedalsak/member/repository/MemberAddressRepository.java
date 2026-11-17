package com.baedalsak.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baedalsak.member.domain.MemberAddress;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

  // 내 주소 목록: 기본 주소 먼저, 나머지는 최근(id 큰) 순
  List<MemberAddress> findByMemberIdOrderByDefaultAddressDescIdDesc(long memberId);

  // 내 주소 하나 — 남의 주소면 빈 값 (→ 404)
  Optional<MemberAddress> findByIdAndMemberId(long id, long memberId);

  // 회원 탈퇴 (#188) 때 함께 지움
  void deleteByMemberId(long memberId);
}
