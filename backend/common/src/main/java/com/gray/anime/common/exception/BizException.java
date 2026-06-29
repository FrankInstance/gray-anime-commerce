package com.gray.anime.common.exception;

public class BizException extends RuntimeException {
    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
