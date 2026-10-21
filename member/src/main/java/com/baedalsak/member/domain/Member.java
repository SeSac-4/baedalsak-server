package com.baedalsak.member.domain;

import java.time.OffsetDateTime;

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

}
