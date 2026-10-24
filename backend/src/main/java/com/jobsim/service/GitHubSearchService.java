package com.jobsim.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobsim.config.LearningProperties;
import com.jobsim.entity.LearningResource;
import com.jobsim.mapper.LearningResourceMapper;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub 搜索与仓库元数据服务（功能 B 数据源）：
 * - search/repositories 按 topic/语言检索高 Star 项目（真实 API，不用本地模拟数据冒充排行）
 * - 读取结果登记到 learning_resource（带来源标记），Star/Fork/时间全部来自 API 返回
 * - 失败（限流/网络/超时）返回明确失败结果，浏览端继续使用本地已缓存数据
 * Star 快照思想：每次刷新时 pushedAt/stars 都真实落库，历史增量需要多个时间点采样，
 * 本版明确不伪造"7 天新增 Star"——采样不足时前端显示"历史数据积累中"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubSearchService {

    private final LearningProperties properties;
    private final LearningResourceMapper resourceMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 技术方向 → GitHub 检索条件（topic + 语言）。分类统一在此维护，前后端共用一套编码。 */
    public static final Map<String, String> TOPIC_QUERIES = Map.ofEntries(
            Map.entry("java-backend", "language:java topic:framework stars:>2000"),
            Map.entry("frontend", "topic:frontend stars:>3000"),
            Map.entry("python", "language:python topic:python stars:>5000"),
            Map.entry("ai-agent", "topic:llm-agent stars:>500"),
            Map.entry("rag", "topic:rag stars:>300"),
            Map.entry("database", "topic:database stars:>2000"),
            Map.entry("devops", "topic:devops stars:>2000"),
            Map.entry("testing", "topic:testing-framework stars:>1000")
    );

    /**
     * 按方向搜索并返回条目（不落库的只读榜单）；失败时 success=false + message。
     * 排序口径：GitHub search 的 stars 降序——是"检索结果内的 Star 排序"，不是全球绝对排名。
     */
    public Map<String, Object> searchTop(String categoryKey, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        String query = TOPIC_QUERIES.getOrDefault(categoryKey, "stars:>5000");
        result.put("categoryKey", categoryKey);
        result.put("sortBasis", "按 GitHub Search API 返回的 Star 数降序（检索条件：" + query + "）");

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(
                        "https://api.github.com/search/repositories?q=" + encode(query)
                                + "&sort=stars&order=desc&per_page=" + Math.min(limit, 30)))
                .timeout(Duration.ofSeconds(properties.getGithubTimeoutSeconds()))
                .header("Accept", "application/vnd.github+json");
        // 无 Token 时完全不发送 Authorization 头（匿名访问 60 次/时）；
        // 发送 "Bearer "（空凭据）会被 GitHub 判 Bad Credentials → 401
        String authHeader = properties.githubAuthHeader();
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
        HttpRequest request = builder.GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // 错误分类：401=凭据无效（Token 配错）；403/429=限流；其余为服务端错误
            if (response.statusCode() == 401) {
                result.put("success", false);
                result.put("errorType", "BAD_CREDENTIALS");
                result.put("message", "GitHub Token 无效或格式错误（HTTP 401）。请检查后端 GITHUB_TOKEN 环境变量（注意前后空格），匿名访问无需 Token");
                return result;
            }
            if (response.statusCode() == 403 || response.statusCode() == 429) {
                // 区分主限额耗尽与次级限流，透出重试时间线索（不暴露任何凭据）
                String reset = response.headers().firstValue("X-RateLimit-Reset").orElse(null);
                String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
                result.put("success", false);
                result.put("errorType", "RATE_LIMIT");
                result.put("rateLimitReset", reset);
                result.put("retryAfterSeconds", retryAfter);
                result.put("message", "GitHub API 限流（HTTP " + response.statusCode()
                        + (retryAfter != null ? "，建议 " + retryAfter + " 秒后重试" : "")
                        + "）。已登记的本地资源不受影响；配置 GITHUB_TOKEN 可提升限额");
                return result;
            }
            if (response.statusCode() != 200) {
                result.put("success", false);
                result.put("errorType", "HTTP_ERROR");
                result.put("message", "GitHub API 返回 HTTP " + response.statusCode() + "（目标：api.github.com/search/repositories）");
                return result;
            }
            JsonNode root = JsonUtils.MAPPER.readTree(response.body());
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("repoFullName", item.path("full_name").asText());
                m.put("description", item.path("description").asText(null));
                m.put("url", item.path("html_url").asText());
                m.put("stars", item.path("stargazers_count").asLong());
                m.put("forks", item.path("forks_count").asLong());
                m.put("language", item.path("language").asText(null));
                m.put("licenseSpdx", item.path("license").path("spdx_id").asText(null));
                m.put("githubCreatedAt", item.path("created_at").asText(null));
                m.put("githubPushedAt", item.path("pushed_at").asText(null));
                m.put("topics", JsonUtils.MAPPER.convertValue(item.path("topics"),
                        JsonUtils.MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)));
                items.add(m);
            }
            result.put("success", true);
            result.put("fetchedAt", LocalDateTime.now().toString());
            result.put("items", items);
            result.put("totalCount", root.path("total_count").asInt());
            return result;
        } catch (java.net.http.HttpTimeoutException e) {
            result.put("success", false);
            result.put("errorType", "TIMEOUT");
            result.put("message", "GitHub API 请求超时（" + properties.getGithubTimeoutSeconds() + "s），请稍后重试；本地资源浏览不受影响");
            return result;
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            result.put("success", false);
            result.put("errorType", "CONNECT_FAILED");
            result.put("message", "无法连接 GitHub（" + e.getClass().getSimpleName() + "），请检查网络；本地资源浏览不受影响");
            return result;
        } catch (Exception e) {
            log.warn("GitHub 搜索失败: {}", e.getMessage());
            result.put("success", false);
            result.put("errorType", "UNKNOWN");
            result.put("message", "GitHub API 调用失败（" + e.getClass().getSimpleName() + "），请查看已登记的本地资源");
            return result;
        }
    }

    private String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
