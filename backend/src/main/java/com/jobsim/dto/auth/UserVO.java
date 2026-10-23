package com.jobsim.dto.auth;

/** 当前用户信息（不含密码哈希等敏感字段） */
public record UserVO(Long id, String username, String nickname, String role) {
}
