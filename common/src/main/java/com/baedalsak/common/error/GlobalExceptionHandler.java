package com.baedalsak.common.error;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;

import lombok.extern.slf4j.Slf4j;

@Slf4j 
@RestControllerAdvice 
public class GlobalExceptionHandler {

  //리턴 값 빌드메서드.
  private ResponseEntity<ErrorResponse> build(ErrorCode code, List<FieldErrorDetail> errors) {
    //traceId넣는 곳도 있어야됨.
    String traceId = MDC.get("traceId");
    return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, errors, traceId));
  }

  //서비스 코드에서 던진 BusinessException
  @ExceptionHandler (BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
    return build(e.getErrorCode(), e.getErrors());

  }

  //@Valid검증실패: 어떤 필드가 틀렸는지 errors에 담기
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidException(MethodArgumentNotValidException e) {
    List<FieldErrorDetail> errors = e.getBindingResult().getFieldErrors().stream()
    .map(f -> new FieldErrorDetail(f.getField(), f.getDefaultMessage()))
    .toList();
    return build(ErrorCode.VALIDATION_FAILED, errors);
  }

  //JSON 형식 오류, enum에 없는 값 등..
  @ExceptionHandler (HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleNotReadable (HttpMessageNotReadableException e){
  return build(ErrorCode.VALIDATION_FAILED, null);
  }

  /** 존재하지 않는 경로 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, null);
    }

    /** 위에서 못 잡은 모든 예외: 내용은 로그로만 남기고 응답에는 일반 메시지만 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return build(ErrorCode.INTERNAL_ERROR, null);
    }
  

}
