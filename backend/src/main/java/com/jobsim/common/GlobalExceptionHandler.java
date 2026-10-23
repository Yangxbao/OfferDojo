package com.jobsim.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把各类异常统一转换为 ApiResponse JSON。
 * 好处：Controller 里不需要写 try-catch，错误响应结构全站一致。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.fail(e.getErrorCode().getCode(), e.getMessage());
    }

    /** @Valid 校验失败（@RequestBody 场景）：返回第一个字段的错误信息即可定位问题 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ApiResponse<Void> handleValidation(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ErrorCode.BAD_REQUEST.getDefaultMessage() : fe.getDefaultMessage();
        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ApiResponse<Void> handleParam(Exception e) {
        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), "请求参数缺失或类型错误");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误，请检查 JSON 是否合法");
    }

    /** 唯一约束冲突（如用户名/任务编码重复）转成友好提示 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> handleDuplicate(DuplicateKeyException e) {
        return ApiResponse.fail(ErrorCode.CONFLICT.getCode(), "记录已存在（用户名或编码重复），请修改后重试");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleDenied(AccessDeniedException e) {
        return ApiResponse.fail(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ApiResponse<Void> handleAuth(AuthenticationException e) {
        return ApiResponse.fail(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getDefaultMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNotFound(NoResourceFoundException e) {
        return ApiResponse.fail(ErrorCode.NOT_FOUND.getCode(), "接口或资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception e) {
        // 只记录日志，绝不把堆栈/内部信息直接返回给前端
        log.error("未处理异常", e);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(), "服务器开小差了，请稍后重试");
    }
}
