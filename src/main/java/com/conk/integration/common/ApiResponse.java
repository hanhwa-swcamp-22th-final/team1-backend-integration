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

    /**
     * 요청 성공 응답을 생성한다.
     *
     * @param data 응답 본문 데이터
     * @return 성공 상태의 ApiResponse
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }

    /**
     * 요청 실패 응답을 생성한다.
     *
     * @param data 오류 정보 데이터
     * @return 실패 상태의 ApiResponse
     */
    public static <T> ApiResponse<T> fail(T data) {
        return new ApiResponse<>(false, data);
    }
}
