package com.baedalsak.member.dto;

import com.baedalsak.member.domain.Member;
import com.baedalsak.member.domain.Role;

public record SignupResponse(
  Long id,
  String email,
  String name,
  Role role
) {
  public static SignupResponse from(Member member) {
    return new SignupResponse(member.getId(), member.getEmail(), member.getName(), member.getRole());
  }
}
