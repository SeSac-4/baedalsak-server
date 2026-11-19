package com.baedalsak.member.domain;

import java.time.OffsetDateTime;

import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@SQLRestriction("deleted_at IS NULL")   // 탈퇴한 회원은 모든 조회에서 빠짐 → 내 정보·내부 조회 404, 알림 문자 안 감 (#188)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member implements Persistable<Long> {

  // 회원번호는 가입 때 시퀀스에서 먼저 받아(MemberRepository.nextId) 직접 넣는다 (#234).
  // Cognito username 이 이 번호라서, INSERT 전에 번호가 있어야 Cognito 를 트랜잭션 밖에서 부를 수 있다
  @Id
  private Long id;

  // 새로 만든 회원인가 — 번호를 직접 넣으면 Spring Data 가 id 만 보고 "이미 있는 행"으로 착각해
  // SELECT 후 merge 하므로, 새 객체인지 따로 알려 준다. DB 에서 읽었거나 저장한 뒤에는 false
  @Transient
  @Getter(AccessLevel.NONE)
  private boolean newEntity = true;

  @Column(nullable = false, unique = true, length = 255)
  private String email;


  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 20)
  private String phone;

  @Enumerated (EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Role role;

  @Column(name = "phone_verified_at")
  private OffsetDateTime phoneVerifiedAt;   // null = 번호 인증 안 함 (#164)

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;   // null = 활성 회원, 값 = 탈퇴 시각 (#188)

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
  

  /** 번호 없이 만든다 — 테스트용. 저장하려면 아래 생성자로 번호를 넣어야 한다 */
  public Member(String email, String name, String phone, Role role) {
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.updatedAt = OffsetDateTime.now();
    }

  /** 가입 (#234): 시퀀스에서 먼저 받은 회원번호로 만든다 (Cognito username 과 같은 번호) */
  public Member(long id, String email, String name, String phone, Role role) {
    this(email, name, phone, role);
    this.id = id;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.newEntity = false;
  }

      /** 휴대폰 인증 성공 (#164) — JPA 가 트랜잭션 끝에 UPDATE (변경 감지) */
  public void markPhoneVerified() {
    this.phoneVerifiedAt = OffsetDateTime.now();
    this.updatedAt = this.phoneVerifiedAt;
  }

  /**
   * 전화번호 변경 (#208). 새 번호로 인증했으면 인증 시각을 지금으로, 인증 없이 바꿨으면(스위치 꺼짐) 미인증으로.
   * 예전 번호의 인증은 새 번호에 이어지지 않는다 — 인증 안 된 번호로는 알림 문자가 가지 않게
   */
  public void changePhone(String phone, boolean verified) {
    OffsetDateTime now = OffsetDateTime.now();
    this.phone = phone;
    this.phoneVerifiedAt = verified ? now : null;
    this.updatedAt = now;
  }

  public boolean isPhoneVerified() {
    return phoneVerifiedAt != null;
  }

  /**
   * 탈퇴 (#188). 행은 남기고(주문·배달이 이 id 를 가리킴) 개인정보는 지운다.
   * 이메일은 UNIQUE 라 바꿔 둬야 같은 이메일로 다시 가입할 수 있다. .invalid 는 실제로 있을 수 없는 주소
   */
  public void withdraw() {
    OffsetDateTime now = OffsetDateTime.now();
    this.deletedAt = now;
    this.email = "deleted-" + id + "@withdrawn.invalid";
    this.name = "탈퇴한 회원";
    this.phone = "000-0000-0000";
    this.phoneVerifiedAt = null;
    this.updatedAt = now;
  }
}
