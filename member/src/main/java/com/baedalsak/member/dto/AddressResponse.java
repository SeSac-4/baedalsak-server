package com.baedalsak.member.dto;

import com.baedalsak.member.domain.MemberAddress;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 배달 주소 (#215) — GET 목록의 한 줄 · POST 응답 */
public record AddressResponse(
    long id,
    String label,
    String address,
    String addressDetail,
    String dong,
    double lat,
    double lng,
    @JsonProperty("isDefault") boolean isDefault
) {

  public static AddressResponse from(MemberAddress a) {
    return new AddressResponse(a.getId(), a.getLabel(), a.getAddress(), a.getAddressDetail(),
        a.getDong(), a.getLat(), a.getLng(), a.isDefaultAddress());
  }

  @Override
  public String toString() {
    return "AddressResponse[id=" + id + ", label=" + label + ", dong=" + dong + ", isDefault=" + isDefault + "]";
  }
}
