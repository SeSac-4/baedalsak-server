package com.baedalsak.member.domain;

import java.time.OffsetDateTime;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity 
@Table(name = "member")
@SQLRestriction("deleted_at IS NULL")   // 탈퇴한 회원은 모든 조회에서 빠짐 → 내 정보·내부 조회 404, 알림 문자 안 감 (#188)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) 
public class Member {
  
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

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
  

  public Member(String email, String name, String phone, Role role) {
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.updatedAt = OffsetDateTime.now();
    }

      /** 휴대폰 인증 성공 (#164) — JPA 가 트랜잭션 끝에 UPDATE (변경 감지) */
  public void markPhoneVerified() {
    this.phoneVerifiedAt = OffsetDateTime.now();
    this.updatedAt = this.phoneVerifiedAt;
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
