package com.baedalsak.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baedalsak.member.domain.Member;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {

  boolean existsByEmail(String email);

  // 가입 전 이메일 인증(#207): 코드를 보내기 전에 대소문자 무시하고 이미 가입됐는지 확인
  boolean existsByEmailIgnoreCase(String email);

  Optional<Member> findByEmail(String email);

  // 주소록 (#215): 주소를 바꾸기 전에 회원 행을 잠근다 (SELECT ... FOR UPDATE).
  // 같은 회원의 요청이 동시에 와도 한 번에 하나씩 → 4개째 추가 · 기본 주소 2개를 막음. 트랜잭션이 끝나면 풀림
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.id = :id")
  Optional<Member> findByIdForUpdate(@Param("id") long id);
}
