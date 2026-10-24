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

    /** 是否配置了有效 Token（仅判断非空白，不打印内容） */
    public boolean hasToken() {
        return githubToken != null && !githubToken.isBlank();
    }

    /**
     * 组装 Authorization 头值。
     * 关键约定：未配置 Token 时返回 null——调用方必须因此「完全不发送」该头，
     * 而不是发送 "Bearer "（空凭据会被 GitHub 判为 Bad Credentials 返回 401；
     * 这正是 /github-top 页面 401 的真实根因）。
     * 匿名访问公开仓库元数据不需要 Token（限额 60 次/时，配置 Token 提升到 5000 次/时）。
     */
    public String githubAuthHeader() {
        return hasToken() ? "Bearer " + githubToken.trim() : null;
    }
}
