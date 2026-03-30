package com.conk.integration.common;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 성공/실패 여부와 payload를 함께 감싸는 공통 응답 래퍼다.
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private boolean success;
    private T data;

    // 정상 응답 팩토리 메서드.
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }

    // 실패 응답 팩토리 메서드.
    public static <T> ApiResponse<T> fail(T data) {
        return new ApiResponse<>(false, data);
    }
}
