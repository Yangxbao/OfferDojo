package com.jobsim.service;

import com.jobsim.common.BusinessException;
import com.jobsim.common.ErrorCode;
import com.jobsim.dto.auth.AuthResponse;
import com.jobsim.dto.auth.LoginRequest;
import com.jobsim.dto.auth.RegisterRequest;
import com.jobsim.dto.auth.UserVO;
import com.jobsim.entity.User;
import com.jobsim.mapper.UserMapper;
import com.jobsim.security.JwtService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：注册 / 登录。
 * 密码使用 BCrypt 单向哈希存储；登录失败统一提示"用户名或密码错误"，
 * 不区分"用户不存在"和"密码错误"，避免账号枚举。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, request.username()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username() : request.nickname());
        user.setRole(ROLE_USER);
        userMapper.insert(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }
        return buildAuthResponse(user);
    }

    public UserVO toUserVO(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.create(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, toUserVO(user));
    }
}
