package com.conk.integration.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 서비스 전반에서 사용하는 비즈니스 에러 코드와 메시지를 모아둔 enum이다.
 */
// 공통 비즈니스 에러 코드 모음.
// INT-001~099: 요청 유효성 오류 (400)
// INT-101~199: 리소스 없음 오류 (404)
// INT-201~299: 상태 충돌 오류 (409)
// INT-301~399: 외부 API 오류 (502/500)
// INT-400~: 공통 HTTP 오류
public enum ErrorCode {

    // 400 Bad Request — 요청 유효성
    INVALID_SELLER_ID(HttpStatus.BAD_REQUEST, "INT-001", "sellerId는 필수입니다."),
    SHIPMENT_BODY_REQUIRED(HttpStatus.BAD_REQUEST, "INT-002", "ShipmentBody는 필수입니다."),
    UNSUPPORTED_BULK_FULFILLMENT(HttpStatus.BAD_REQUEST, "INT-003", "해당 채널은 일괄 fulfillment를 지원하지 않습니다."),
    UNSUPPORTED_CHANNEL(HttpStatus.BAD_REQUEST, "INT-004", "지원하지 않는 fulfillment 채널입니다."),

    // 403 Forbidden — 접근 권한 없음
    SELLER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "INT-403", "해당 주문에 대한 접근 권한이 없습니다."),

    // 404 Not Found — 리소스 없음
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "INT-101", "주문을 찾을 수 없습니다."),
    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "INT-102", "송장을 찾을 수 없습니다."),
    CHANNEL_CREDENTIALS_NOT_FOUND(HttpStatus.NOT_FOUND, "INT-103", "Shopify 자격증명을 찾을 수 없습니다."),
    NO_SHIPPING_RATES(HttpStatus.NOT_FOUND, "INT-104", "운임 정보가 없습니다."),
    CHANNEL_CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "INT-404", "연결된 채널 정보를 찾을 수 없습니다."),

    // 409 Conflict — 상태 충돌
    INVOICE_ALREADY_EXISTS(HttpStatus.CONFLICT, "INT-201", "이미 송장이 발급된 주문입니다."),
    ORDER_NOT_INVOICED(HttpStatus.CONFLICT, "INT-202", "송장이 발급되지 않은 주문입니다."),

    // 502 Bad Gateway / 500 — 외부 API 오류
    SHOPIFY_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "INT-301", "Shopify API 응답이 비어있습니다."),
    SHOPIFY_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INT-302", "Shopify API 요청 직렬화에 실패했습니다."),
    EASYPOST_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "INT-303", "EasyPost API 응답이 비어있습니다."),
    EASYPOST_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INT-304", "EasyPost API 요청 직렬화에 실패했습니다."),

    // 공통 HTTP 오류
    MISSING_REQUEST_HEADER(HttpStatus.BAD_REQUEST, "INT-400", "필수 헤더가 누락되었습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "INT-405", "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INT-500", "서버 내부에서 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
