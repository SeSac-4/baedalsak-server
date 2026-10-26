package com.baedalsak.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baedalsak.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

  boolean existsByEmail(String email);

  Optional<Member> findByEmail(String email);


  
} 
