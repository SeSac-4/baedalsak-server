package com.baedalsak.common.error;

import java.util.List;

import lombok.Getter;

@Getter 
public class BusinessException extends RuntimeException {
  
  private final ErrorCode errorCode;
  private final List<ErrorResponse.FieldErrorDetail> errors;

  public BusinessException(ErrorCode errorCode){
    this(errorCode, null);
  }

  public BusinessException(ErrorCode errorCode, List<ErrorResponse.FieldErrorDetail> errors) {
    super(errorCode.getMessage());   // 기존에 쓰던 방식 유지
    this.errorCode = errorCode;
    this.errors = errors;
  }

}
