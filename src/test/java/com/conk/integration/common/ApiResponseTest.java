package com.conk.integration.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse 테스트")
class ApiResponseTest {

    @Test
    @DisplayName("응답 데이터가 주어지면 ok를 호출했을 때 success가 true인 응답을 반환해야 한다")
    void ok_returnsSuccessTrue() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("payload");
    }

    @Test
    @DisplayName("응답 데이터가 주어지면 fail을 호출했을 때 success가 false인 응답을 반환해야 한다")
    void fail_returnsSuccessFalse() {
        ApiResponse<String> response = ApiResponse.fail("error");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isEqualTo("error");
    }
}
