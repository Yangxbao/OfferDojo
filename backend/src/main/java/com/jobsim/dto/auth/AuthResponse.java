package com.jobsim.dto.auth;

/** 登录成功响应：token + 用户信息 */
public record AuthResponse(String token, UserVO user) {
}
