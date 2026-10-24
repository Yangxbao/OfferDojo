package com.jobsim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 资源中心配置。
 * githubToken 仅从环境变量注入，用于提升 GitHub API 限额；不配置也可用（低限额），
 * 绝不写入代码、日志或返回给浏览器。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jobsim.learning")
public class LearningProperties {

    /** 可选：GitHub Token（环境变量 GITHUB_TOKEN 注入），仅用于提升 API 限额 */
    private String githubToken = "";

    /** GitHub API 超时（秒） */
    private int githubTimeoutSeconds = 15;

    /** 组装 Authorization 头：未配置 Token 时返回空 Map 等价的匿名头 */
    public String githubAuthHeader() {
        return githubToken == null || githubToken.isBlank()
                ? "Bearer "
                : "Bearer " + githubToken;
    }
}
