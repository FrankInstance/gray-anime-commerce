package com.gray.anime.common.exception;

import com.gray.anime.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void preservesTheBusinessExceptionHttpStatus() {
        BizException exception = new BizException(
                HttpStatus.TOO_MANY_REQUESTS,
                "LOGIN_RATE_LIMITED",
                "登录尝试过于频繁，请稍后再试。"
        );

        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler().handleBiz(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("LOGIN_RATE_LIMITED");
    }
}
