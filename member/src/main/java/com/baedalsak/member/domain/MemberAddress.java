package com.baedalsak.member.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 고객 배달 주소 (#215). 고객당 최대 3개, 기본 주소 1개 — 규칙은 MemberAddressService 가 지킨다 */
@Entity
@Table(name = "member_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAddress {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;   // 프로필 엔티티처럼 연관관계 없이 번호만

  @Column(length = 20)
  private String label;   // 별칭 (집 · 회사), 선택

  @Column(nullable = false, length = 200)
  private String address;   // 도로명 주소

  @Column(name = "address_detail", length = 50)
  private String addressDetail;   // 동 · 호수, 선택

  @Column(nullable = false, length = 50)
  private String dong;   // 주문의 deliveryDong 으로 그대로 쓴다

  @Column(nullable = false)
  private double lat;

  @Column(nullable = false)
  private double lng;

  @Column(name = "is_default", nullable = false)
  private boolean defaultAddress;   // getter: isDefaultAddress()

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;   // DB 기본값 now()

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public MemberAddress(long memberId, String label, String address, String addressDetail,
      String dong, double lat, double lng, boolean defaultAddress) {
    this.memberId = memberId;
    this.label = label;
    this.address = address;
    this.addressDetail = addressDetail;
    this.dong = dong;
    this.lat = lat;
    this.lng = lng;
    this.defaultAddress = defaultAddress;
    this.updatedAt = OffsetDateTime.now();
  }

  /** 주소 내용 바꾸기 (기본 주소 여부는 makeDefault · clearDefault 로 따로) */
  public void update(String label, String address, String addressDetail, String dong, double lat, double lng) {
    this.label = label;
    this.address = address;
    this.addressDetail = addressDetail;
    this.dong = dong;
    this.lat = lat;
    this.lng = lng;
    this.updatedAt = OffsetDateTime.now();
  }

  public void makeDefault() {
    this.defaultAddress = true;
    this.updatedAt = OffsetDateTime.now();
  }

  public void clearDefault() {
    if (!defaultAddress) {
      return;   // 안 바뀌면 updatedAt 도 그대로 (쓸데없는 UPDATE 방지)
    }
    this.defaultAddress = false;
    this.updatedAt = OffsetDateTime.now();
  }
}
