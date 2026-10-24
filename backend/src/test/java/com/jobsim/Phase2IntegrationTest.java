package com.jobsim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第二阶段集成测试：八股补题、社区投稿、用户项目、GitHub 榜单降级。
 * 覆盖：状态机流转、用户隔离、授权控制（未授权不得公开发布）、未审核不进公共区。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase2IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;

    private static String userToken;
    private static String otherUserToken;
    private static String adminToken;
    private static Long projectId;
    private static Long submissionId;

    private HttpHeaders auth(String token, boolean json) {
        HttpHeaders h = new HttpHeaders();
        if (json) h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return h;
    }

    private JsonNode body(String json) {
        try { return objectMapper.readTree(json); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private JsonNode get(String url, String token) {
        return body(rest.exchange(url, HttpMethod.GET, new HttpEntity<>(auth(token, false)), String.class).getBody());
    }

    private String register(String username) {
        return body(rest.postForEntity("/api/auth/register",
                new HttpEntity<>(Map.of("username", username, "password", "pass1234"), auth(null, true)),
                String.class).getBody()).path("data").path("token").asText();
    }

    @Test
    @Order(1)
    void setup() {
        userToken = register("p2user");
        otherUserToken = register("p2other");
        adminToken = body(rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("username", "admin", "password", "admin123"), auth(null, true)),
                String.class).getBody()).path("data").path("token").asText();
        assertThat(userToken).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("八股补题：8 个原空分类各有 ≥5 题，总数 125")
    void baguPhase2() {
        for (String cat : new String[]{"api-design", "troubleshooting", "testing", "refactoring",
                "distributed", "system-design", "engineering", "ai-app"}) {
            JsonNode page = get("/api/bagu/questions?category=" + cat + "&size=20", userToken).path("data");
            assertThat(page.path("total").asLong()).as("分类 %s 应≥5 题", cat).isGreaterThanOrEqualTo(5);
        }
        JsonNode all = get("/api/bagu/questions?size=1", userToken).path("data");
        assertThat(all.path("total").asLong()).isEqualTo(125);
        // 新题带 mistakes（常见错误）等扩展字段（新题才有，旧题无此字段不影响）
    }

    @Test
    @Order(3)
    @DisplayName("用户项目全生命周期：草稿→提交→通过→公开展示；未通过不进公共区")
    void projectLifecycle() {
        // 新建草稿
        Map<String, Object> draft = Map.of(
                "name", "校园二手交易平台",
                "summary", "Spring Boot + Vue 的二手交易平台课程设计",
                "techStack", "Java,Spring Boot,Vue,MySQL",
                "category", "java-backend",
                "myRole", "独立完成后端与数据库设计",
                "aiAssisted", true,
                "aiUsageNote", "AI 辅助了单元测试编写，业务逻辑自己完成");
        JsonNode created = body(rest.postForEntity("/api/my/projects",
                new HttpEntity<>(draft, auth(userToken, true)), String.class).getBody());
        assertThat(created.get("code").asInt()).isZero();
        projectId = created.path("data").path("id").asLong();
        assertThat(created.path("data").path("status").asText()).isEqualTo("DRAFT");

        // 提交前公共区不可见
        assertThat(get("/api/projects?size=50", userToken).path("data").path("total").asLong()).isZero();

        // 提交审核
        JsonNode submitted = body(rest.exchange("/api/my/projects/" + projectId + "/submit",
                HttpMethod.PUT, new HttpEntity<>(auth(userToken, false)), String.class).getBody());
        assertThat(submitted.path("data").path("status").asText()).isEqualTo("PENDING_REVIEW");
        // 提交后不可编辑
        assertThat(rest.exchange("/api/my/projects/" + projectId, HttpMethod.PUT,
                new HttpEntity<>(draft, auth(userToken, true)), String.class)
                .getStatusCode().value()).isEqualTo(200); // Business 异常 → HTTP 200 + code
        JsonNode editReject = body(rest.exchange("/api/my/projects/" + projectId, HttpMethod.PUT,
                new HttpEntity<>(draft, auth(userToken, true)), String.class).getBody());
        assertThat(editReject.path("code").asInt()).isEqualTo(400);

        // 他人不可操作
        assertThat(body(rest.exchange("/api/my/projects/" + projectId + "/withdraw", HttpMethod.PUT,
                new HttpEntity<>(auth(otherUserToken, false)), String.class).getBody())
                .path("code").asInt()).isEqualTo(403);

        // 普通用户不能审核
        assertThat(body(rest.exchange("/api/admin/projects/" + projectId + "/review", HttpMethod.PUT,
                new HttpEntity<>(Map.of("approve", true), auth(userToken, true)), String.class).getBody())
                .path("code").asInt()).isEqualTo(403);

        // 管理员驳回 → 带意见；重新提交 → 通过 → 公共区可见
        rest.exchange("/api/admin/projects/" + projectId + "/review", HttpMethod.PUT,
                new HttpEntity<>(Map.of("approve", false, "comment", "补充项目难点描述"), auth(adminToken, true)), String.class);
        JsonNode resubmitted = body(rest.exchange("/api/my/projects/" + projectId + "/submit",
                HttpMethod.PUT, new HttpEntity<>(auth(userToken, false)), String.class).getBody());
        assertThat(resubmitted.path("code").asInt()).isZero();

        rest.exchange("/api/admin/projects/" + projectId + "/review", HttpMethod.PUT,
                new HttpEntity<>(Map.of("approve", true, "comment", "符合展示标准"), auth(adminToken, true)), String.class);
        assertThat(get("/api/projects?size=50", userToken).path("data").path("total").asLong()).isEqualTo(1);
        assertThat(get("/api/projects/" + projectId, userToken).path("data").path("name").asText())
                .contains("二手交易");
    }

    @Test
    @Order(4)
    @DisplayName("社区投稿：未授权公开展示的投稿不能被发布到公共题库")
    void submissionAuthorization() {
        // 授权「仅审核」的投稿：管理员审核通过时必须被拒绝发布
        Map<String, Object> reviewOnly = Map.of(
                "category", "java-core", "questionType", "BAGU", "difficulty", "MEDIUM",
                "title", "HashMap 是线程安全的吗？",
                "content", "HashMap 非线程安全，并发场景用 ConcurrentHashMap",
                "licenseGrant", "REVIEW_ONLY");
        JsonNode r1 = body(rest.postForEntity("/api/my/bagu-submissions",
                new HttpEntity<>(reviewOnly, auth(userToken, true)), String.class).getBody());
        assertThat(r1.get("code").asInt()).isZero();
        Long reviewOnlyId = r1.path("data").path("id").asLong();
        rest.exchange("/api/my/bagu-submissions/" + reviewOnlyId + "/submit", HttpMethod.PUT,
                new HttpEntity<>(auth(userToken, false)), String.class);
        JsonNode approveFail = body(rest.exchange("/api/admin/bagu-submissions/" + reviewOnlyId + "/review",
                HttpMethod.PUT, new HttpEntity<>(Map.of("approve", true), auth(adminToken, true)), String.class).getBody());
        assertThat(approveFail.path("code").asInt()).isEqualTo(400); // 未授权公开展示

        // 授权公开展示的投稿：正常流转进入公共题库
        Map<String, Object> public1 = Map.of(
                "category", "system-design", "questionType", "INTERVIEW", "difficulty", "HARD",
                "title", "某厂实习面试：设计一个短链服务",
                "content", "面试复盘：考察发号器设计、301/302 选择与缓存策略",
                "myAnswer", "采用号段模式发号 + Redis 缓存 + 布隆防穿透",
                "licenseGrant", "PUBLIC_DISPLAY", "anonymous", true);
        JsonNode r2 = body(rest.postForEntity("/api/my/bagu-submissions",
                new HttpEntity<>(public1, auth(userToken, true)), String.class).getBody());
        submissionId = r2.path("data").path("id").asLong();
        rest.exchange("/api/my/bagu-submissions/" + submissionId + "/submit", HttpMethod.PUT,
                new HttpEntity<>(auth(userToken, false)), String.class);
        rest.exchange("/api/admin/bagu-submissions/" + submissionId + "/review", HttpMethod.PUT,
                new HttpEntity<>(Map.of("approve", true, "comment", "内容详实"), auth(adminToken, true)), String.class);

        // 公共题库可见且匿名
        JsonNode community = get("/api/bagu/community?size=50", userToken).path("data");
        assertThat(community.path("total").asLong()).isEqualTo(1);
        assertThat(community.path("list").get(0).path("authorName").asText()).isEqualTo("匿名同学");
        assertThat(community.path("list").get(0).path("originLabel").asText()).contains("用户");

        // 他人不可删
        assertThat(body(rest.exchange("/api/my/bagu-submissions/" + submissionId, HttpMethod.DELETE,
                new HttpEntity<>(auth(otherUserToken, false)), String.class).getBody())
                .path("code").asInt()).isEqualTo(403);
    }

    @Test
    @Order(5)
    @DisplayName("GitHub 榜单：接口可用；失败时返回明确失败信息不影响浏览")
    void githubTopGraceful() {
        JsonNode cats = get("/api/github/top/categories", userToken);
        assertThat(cats.path("data").size()).isGreaterThanOrEqualTo(8);
        // 真实调用可能成功或因网络/限流失败——两种都必须结构正确且不崩
        JsonNode top = get("/api/github/top?category=java-backend&limit=5", userToken).path("data");
        assertThat(top.has("success")).isTrue();
        assertThat(top.has("sortBasis")).isTrue(); // 排序口径必须可解释
        if (top.path("success").asBoolean()) {
            assertThat(top.path("items").size()).isGreaterThan(0);
            assertThat(top.path("items").get(0).path("stars").asLong()).isGreaterThan(0);
        } else {
            assertThat(top.path("message").asText()).isNotBlank();
        }
    }

    @Test
    @Order(6)
    @DisplayName("项目安全：非法 URL 与非法分类被拒绝")
    void projectSecurity() {
        Map<String, Object> bad = Map.of(
                "name", "x", "summary", "s", "techStack", "t", "category", "java-backend",
                "repoUrl", "javascript:alert(1)");
        assertThat(body(rest.postForEntity("/api/my/projects",
                new HttpEntity<>(bad, auth(userToken, true)), String.class).getBody())
                .path("code").asInt()).isEqualTo(400);

        Map<String, Object> badCat = Map.of(
                "name", "x", "summary", "s", "techStack", "t", "category", "not-exist");
        assertThat(body(rest.postForEntity("/api/my/projects",
                new HttpEntity<>(badCat, auth(userToken, true)), String.class).getBody())
                .path("code").asInt()).isEqualTo(400);
    }
}
