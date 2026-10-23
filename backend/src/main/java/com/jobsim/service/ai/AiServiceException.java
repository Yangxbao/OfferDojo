package com.jobsim.service.ai;

/**
 * AI 服务异常：区分错误类型，便于上层决定提示文案与降级策略。
 * message 只包含安全信息（状态码、错误类别），绝不携带 API Key。
 */
public class AiServiceException extends RuntimeException {

    public static final String TIMEOUT = "TIMEOUT";
    public static final String HTTP_ERROR = "HTTP_ERROR";
    public static final String PARSE_ERROR = "PARSE_ERROR";
    public static final String UNKNOWN = "UNKNOWN";

    private final String errorType;

    public AiServiceException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }
}
