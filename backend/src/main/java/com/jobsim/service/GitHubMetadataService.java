package com.jobsim.service;

import com.jobsim.config.LearningProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GitHub 元数据核验服务：调用 GitHub REST API 获取仓库的默认分支、最近提交与许可证信息。
 * 设计约束：
 * - 只读取元数据，不克隆仓库、不执行外部代码
 * - 无 GITHUB_TOKEN 也可调用（限额 60 次/小时足够管理端手动核验）；配置 Token 可提升限额
 * - 任何失败（网络/限流/404）都返回核验失败结果，绝不把失败伪装成"已核验"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubMetadataService {

    private final LearningProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 核验结果：success=true 时填充元数据；false 时 message 说明原因（网络/限流/404）。
     */
    public Map<String, Object> verify(String repoUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        String repoPath = extractRepoPath(repoUrl);
        if (repoPath == null) {
            result.put("success", false);
            result.put("message", "无法从链接解析出 GitHub 仓库路径（仅支持 github.com/{owner}/{repo} 形式）");
            return result;
        }
        result.put("repoPath", repoPath);

        long start = System.currentTimeMillis();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repoPath))
                .timeout(Duration.ofSeconds(properties.getGithubTimeoutSeconds()))
                .header("Accept", "application/vnd.github+json");
        // 无 Token 时不发送 Authorization 头（匿名可访问公开仓库元数据，60 次/时）
        String authHeader = properties.githubAuthHeader();
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
        HttpRequest request = builder.GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            result.put("durationMs", System.currentTimeMillis() - start);
            if (response.statusCode() == 404) {
                result.put("success", false);
                result.put("errorType", "NOT_FOUND");
                result.put("message", "仓库不可访问（404）：可能已删除、转私有或链接错误");
                return result;
            }
            if (response.statusCode() == 401) {
                result.put("success", false);
                result.put("errorType", "BAD_CREDENTIALS");
                result.put("message", "GitHub Token 无效（401）。请检查后端 GITHUB_TOKEN 配置；匿名访问无需 Token");
                return result;
            }
            if (response.statusCode() == 403 || response.statusCode() == 429) {
                result.put("success", false);
                result.put("errorType", "RATE_LIMIT");
                result.put("message", "GitHub API 限流（" + response.statusCode() + "），请稍后重试或配置 GITHUB_TOKEN");
                return result;
            }
            if (response.statusCode() != 200) {
                result.put("success", false);
                result.put("errorType", "HTTP_ERROR");
                result.put("message", "GitHub API 返回 HTTP " + response.statusCode());
                return result;
            }
            JsonNode root = JsonUtils.MAPPER.readTree(response.body());
            result.put("success", true);
            result.put("defaultBranch", root.path("default_branch").asText(null));
            result.put("pushedAt", root.path("pushed_at").asText(null));
            result.put("stars", root.path("stargazers_count").asInt());
            result.put("description", root.path("description").asText(null));
            result.put("licenseSpdx", root.path("license").path("spdx_id").asText(null));
            result.put("licenseName", root.path("license").path("name").asText(null));
            return result;
        } catch (java.net.http.HttpTimeoutException e) {
            result.put("success", false);
            result.put("message", "GitHub API 调用超时");
            return result;
        } catch (Exception e) {
            log.warn("GitHub 元数据核验失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "GitHub API 调用失败（网络错误），资源中心本地浏览不受影响");
            return result;
        }
    }

    /** 从 URL 提取 {owner}/{repo}；非 github.com 链接返回 null */
    private String extractRepoPath(String url) {
        if (url == null) {
            return null;
        }
        var matcher = java.util.regex.Pattern
                .compile("github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)")
                .matcher(url);
        return matcher.find() ? matcher.group(1) + "/" + matcher.group(2) : null;
    }
}
