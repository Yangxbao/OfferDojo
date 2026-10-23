package com.jobsim.security;

/**
 * 登录用户主体：JWT 校验通过后放入 SecurityContext，
 * Controller 通过 @AuthenticationPrincipal 直接拿到当前用户，避免到处传 HttpServletRequest。
 */
public record LoginUser(Long id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
