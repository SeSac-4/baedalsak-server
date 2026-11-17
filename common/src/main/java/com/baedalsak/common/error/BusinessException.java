package com.baedalsak.common.error;

import java.util.List;

import lombok.Getter;

@Getter 
public class BusinessException extends RuntimeException {
  
  private final ErrorCode errorCode;
  private final List<ErrorResponse.FieldErrorDetail> errors;

  public BusinessException(ErrorCode errorCode){
    this(errorCode, (List<ErrorResponse.FieldErrorDetail>) null);
  }

  public BusinessException(ErrorCode errorCode, List<ErrorResponse.FieldErrorDetail> errors) {
    super(errorCode.getMessage());   // 기존에 쓰던 방식 유지
    this.errorCode = errorCode;
    this.errors = errors;
  }

  // 같은 코드라도 상황에 맞는 문구가 필요할 때 (예: FORBIDDEN 을 픽업 · 완료에서 다른 문구로). code 는 그대로라 프론트 분기에 영향 없음 (#230)
  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
    this.errors = null;
  }

}
