package com.baedalsak.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity 
@Table(name = "owner_profile")
@Getter 
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnerProfile {

  @Id 
  @Column(name = "member_id")
  private Long memberId;

  @Column (name = "store_name_at_signup", nullable = false, length = 100)
  private String storeNameAtSignup;

  public OwnerProfile(Long memberId, String storeNameAtSignup) {
    this.memberId = memberId;
    this.storeNameAtSignup = storeNameAtSignup;
  }

  
  
}
