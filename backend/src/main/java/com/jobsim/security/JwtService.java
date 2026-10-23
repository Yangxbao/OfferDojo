package com.jobsim.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与解析。
 * 设计取舍：无状态 JWT，服务端不存会话；角色直接写进 claim，权限校验不再查库。
 * 密钥来自配置（环境变量注入）；解析失败一律返回 null，由过滤器按"未登录"处理。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlHours;

    public JwtService(@Value("${jobsim.jwt.secret}") String secret,
                      @Value("${jobsim.jwt.ttl-hours}") long ttlHours) {
        // HS256 要求密钥至少 32 字节；配置过短时这里快速失败，避免带病启动
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlHours = ttlHours;
    }

    public String create(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token 为登录用户；token 无效/过期返回 null。
     * 不在解析层抛异常，让调用方（过滤器）可以选择"继续放行为匿名"。
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Object uidRaw = claims.get("uid");
            if (!(uidRaw instanceof Number uidNum)) {
                return null;
            }
            String role = claims.get("role", String.class);
            return new LoginUser(uidNum.longValue(), claims.getSubject(), role);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
