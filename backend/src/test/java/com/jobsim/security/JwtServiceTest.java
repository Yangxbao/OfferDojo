package com.jobsim.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 服务的单元测试：签发-解析往返、非法 token、过期 token。
 */
class JwtServiceTest {

    private final JwtService jwtService =
            new JwtService("test-secret-key-for-jobsim-unit-test-32bytes!", 1);

    @Test
    @DisplayName("签发的 token 能被解析回用户信息")
    void createThenParse() {
        String token = jwtService.create(42L, "alice", "ADMIN");
        LoginUser user = jwtService.parse(token);
        assertThat(user).isNotNull();
        assertThat(user.id()).isEqualTo(42L);
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.role()).isEqualTo("ADMIN");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("非法 token 解析返回 null 而不是抛异常")
    void invalidTokenReturnsNull() {
        assertThat(jwtService.parse("not-a-jwt")).isNull();
        assertThat(jwtService.parse("")).isNull();
    }

    @Test
    @DisplayName("被篡改的 token 解析返回 null")
    void tamperedTokenReturnsNull() {
        String token = jwtService.create(1L, "bob", "USER");
        LoginUser user = jwtService.parse(token + "x");
        assertThat(user).isNull();
    }

    @Test
    @DisplayName("过期 token 解析返回 null")
    void expiredTokenReturnsNull() {
        JwtService expiredService =
                new JwtService("test-secret-key-for-jobsim-unit-test-32bytes!", -1);
        String token = expiredService.create(1L, "bob", "USER");
        assertThat(expiredService.parse(token)).isNull();
    }
}
