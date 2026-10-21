package com.baedalsak.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity 
@Table (name = "rider_profile")
@Getter 
@NoArgsConstructor (access = AccessLevel.PROTECTED)
public class RiderProfile {

  @Id 
  @Column (name = "member_id")
  private Long memberId;

  @Column (name = "vehicle_type", nullable = false, length = 10)
  private String vehicleType;

  public RiderProfile(Long memberId, String vehicleType) {
    this.memberId = memberId;
    this.vehicleType = vehicleType;
  }

  
}
