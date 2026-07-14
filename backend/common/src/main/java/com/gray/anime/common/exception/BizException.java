package com.gray.anime.common.exception;

import org.springframework.http.HttpStatus;

public class BizException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BizException(String code, String message) {
        this(HttpStatus.BAD_REQUEST, code, message);
    }

    public BizException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
