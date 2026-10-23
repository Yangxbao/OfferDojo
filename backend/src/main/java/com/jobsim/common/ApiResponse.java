package com.jobsim.common;

/**
 * 统一响应包装：所有接口返回 {code, message, data}。
 * code = 0 表示成功；非 0 时与 HTTP 状态码保持一致，前端只需处理一种约定。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
