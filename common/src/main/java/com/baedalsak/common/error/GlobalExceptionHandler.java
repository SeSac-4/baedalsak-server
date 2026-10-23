package com.baedalsak.common.error;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.baedalsak.common.error.ErrorResponse.FieldErrorDetail;

import jakarta.validation.ConstraintViolationException;
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
    return build(CommonErrorCode.VALIDATION_FAILED, errors);
  }

  //JSON 형식 오류, enum에 없는 값 등..
  @ExceptionHandler (HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleNotReadable (HttpMessageNotReadableException e){
  return build(CommonErrorCode.VALIDATION_FAILED, null);
  }

  //경로·쿼리 값 타입 오류 (예: /stores/abc 처럼 숫자 자리에 문자)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    return build(CommonErrorCode.VALIDATION_FAILED,
        List.of(new FieldErrorDetail(e.getName(), "값의 형식이 올바르지 않습니다.")));
  }

  //필수 쿼리 파라미터 없음
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
    return build(CommonErrorCode.VALIDATION_FAILED,
        List.of(new FieldErrorDetail(e.getParameterName(), "필수 값입니다.")));
  }

  //@PathVariable·@RequestParam 검증 실패 (@Min 등). 경로 "getStore.storeId"에서 마지막 이름만 field로
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
    List<FieldErrorDetail> errors = e.getConstraintViolations().stream()
    .map(v -> {
      String path = v.getPropertyPath().toString();
      return new FieldErrorDetail(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
    })
    .toList();
    return build(CommonErrorCode.VALIDATION_FAILED, errors);
  }

  //위와 같은 검증 실패인데 컨트롤러에 @Validated가 없을 때 (Spring 6.1+ 기본 방식)
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
    List<FieldErrorDetail> errors = e.getParameterValidationResults().stream()
    .flatMap(r -> r.getResolvableErrors().stream()
        .map(err -> new FieldErrorDetail(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
    .toList();
    return build(CommonErrorCode.VALIDATION_FAILED, errors);
  }

  //허용되지 않는 HTTP 메서드 (예: 조회 전용 경로에 POST). 없으면 아래 Exception 처리기가 500으로 바꿔 버림
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    return build(CommonErrorCode.METHOD_NOT_ALLOWED, null);
  }

  /** 존재하지 않는 경로 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return build(CommonErrorCode.NOT_FOUND, null);
    }

    /** 위에서 못 잡은 모든 예외: 내용은 로그로만 남기고 응답에는 일반 메시지만 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return build(CommonErrorCode.INTERNAL_ERROR, null);
    }
  

}
