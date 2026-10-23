package com.jobsim.security;

import com.jobsim.common.ApiResponse;
import com.jobsim.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未登录（401）/无权限（403）时返回 JSON 而不是 Spring 默认的 403 页面，
 * 与全站 ApiResponse 结构保持一致，前端拦截器可以统一识别。
 */
public class SecurityResponses {

    private SecurityResponses() {
    }

    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJson(ApiResponse.fail(status, message)));
    }

    @Component
    public static class EntryPoint implements AuthenticationEntryPoint {
        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            SecurityResponses.write(response, 401, "未登录或登录已过期");
        }
    }

    @Component
    public static class DeniedHandler implements AccessDeniedHandler {
        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           AccessDeniedException accessDeniedException) throws IOException {
            SecurityResponses.write(response, 403, "没有权限执行该操作");
        }
    }
}
