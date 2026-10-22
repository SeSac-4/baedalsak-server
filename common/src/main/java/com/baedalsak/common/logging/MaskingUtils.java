package com.baedalsak.common.logging;

//로그에 개인정보 남기지 않기위함.
public final class MaskingUtils {

  private MaskingUtils() {

  }

  /** 010-1234-5678, 01012345678 → 010-****-5678 */
  public static String maskPhone(String phone) {
    if (phone == null) {
      return null;
    }
    String digits = phone.replaceAll("\\D", ""); // 숫자가 아닌 문자(하이픈 등) 제거
    if (digits.length() < 8) {
      return "****"; // 형식이 이상하면 통째로 가림
    }
    return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
  }

}
