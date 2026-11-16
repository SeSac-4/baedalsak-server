package com.baedalsak.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baedalsak.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

  boolean existsByEmail(String email);

  // 가입 전 이메일 인증(#207): 코드를 보내기 전에 대소문자 무시하고 이미 가입됐는지 확인
  boolean existsByEmailIgnoreCase(String email);

  Optional<Member> findByEmail(String email);


  
} 
