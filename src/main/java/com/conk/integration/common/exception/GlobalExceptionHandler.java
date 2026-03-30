package com.conk.integration.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

// 컨트롤러 밖으로 나온 예외를 API 응답 형식으로 통일한다.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 중 발생하는 잘못된 인자 예외 처리 (HTTP 400 Bad Request)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(false, ex.getMessage()));
    }

    /**
     * @RequestHeader 등 필수 파라미터가 누락되었을 때 발생하는 예외 처리 (HTTP 400 Bad Request)
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        log.warn("MissingRequestHeaderException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(false, "필수 헤더가 누락되었습니다: " + ex.getHeaderName()));
    }

    /**
     * 지원하지 않는 HTTP 메서드 호출 시 405로 응답한다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("HttpRequestMethodNotSupportedException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(false, "지원하지 않는 HTTP 메서드입니다: " + ex.getMethod()));
    }

    /**
     * 상태나 흐름이 잘못된 상태일 때 발생하는 예외 처리 (HTTP 409 Conflict 또는 400)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(false, ex.getMessage()));
    }

    /**
     * 그 외 모든 예측하지 못한 시스템 예외 처리 (HTTP 500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unhandled Exception: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(false, "서버 내부에서 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    // 에러 응답의 최소 공통 구조다.
    public record ErrorResponse(boolean success, String message) {}
}
