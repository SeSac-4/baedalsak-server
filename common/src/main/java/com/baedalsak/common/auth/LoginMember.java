package com.baedalsak.common.auth;

public record LoginMember(Long id, MemberRole role) {
  public static final String ID_HEADER = "X-Member-Id";
  public static final String ROLE_HEADER = "X-Member-Role";
}
