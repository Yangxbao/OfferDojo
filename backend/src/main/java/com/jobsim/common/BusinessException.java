package com.jobsim.common;

import lombok.Getter;

/**
 * 业务异常：Service 层用"抛业务异常"代替返回 null 或布尔值，
 * 由 GlobalExceptionHandler 统一转换为 ApiResponse。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
