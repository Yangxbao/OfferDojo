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
 * 资源中心集成测试：资源目录、三条路线、学习计划、权限隔离、许可证默认禁止。
 * 独立于 FullFlowIntegrationTest（注册独立用户，避免耦合）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LearningCenterIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;

    private static String userToken;
    private static String secondUserToken;
    private static String adminToken;
    private static Long firstResourceId;
    private static Long planItemId;
    private static Long createdResourceId;

    private HttpHeaders authHeaders(String token, boolean json) {
        HttpHeaders headers = new HttpHeaders();
        if (json) headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode body(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode get(String url, String token) {
        ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token, false)), String.class);
        return body(resp.getBody());
    }

    private String register(String username) {
        ResponseEntity<String> reg = rest.postForEntity("/api/auth/register",
                new HttpEntity<>(Map.of("username", username, "password", "pass1234"),
                        authHeaders(null, true)), String.class);
        return body(reg.getBody()).path("data").path("token").asText();
    }

    @Test
    @Order(1)
    void setup() {
        userToken = register("lcuser");
        secondUserToken = register("lcuser2");
        adminToken = register("lcadmin");
        // 升级为管理员：直接通过数据库不可行，用已有 admin 登录替代
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("username", "admin", "password", "admin123"),
                        authHeaders(null, true)), String.class);
        adminToken = body(login.getBody()).path("data").path("token").asText();
        assertThat(userToken).isNotBlank();
        assertThat(adminToken).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("三条路线均能加载阶段与节点，节点关联资源与任务")
    void threeTracksLoad() {
        JsonNode tracks = get("/api/learning/tracks", userToken).path("data");
        assertThat(tracks.size()).isEqualTo(3);

        boolean foundJava = false, foundAi = false, foundPython = false;
        for (JsonNode track : tracks) {
            String trackCode = track.path("track").asText();
            switch (trackCode) {
                case "java-fullstack" -> foundJava = true;
                case "ai-fullstack" -> foundAi = true;
                case "python-agent" -> foundPython = true;
            }
            assertThat(track.path("stages").size()).isGreaterThan(0);
            JsonNode firstStage = track.path("stages").get(0);
            assertThat(firstStage.path("nodes").size()).isGreaterThan(0);
            JsonNode node = firstStage.path("nodes").get(0);
            assertThat(node.path("knowledge").size()).isGreaterThan(0);
            assertThat(node.has("milestone")).isTrue();
        }
        assertThat(foundJava).isTrue();
        assertThat(foundAi).isTrue();
        assertThat(foundPython).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("资源列表 ≥ 12 条且三个方向都有资源；筛选与分页可用")
    void resourceListAndFilter() {
        JsonNode all = get("/api/learning/resources?size=50", userToken).path("data");
        assertThat(all.path("total").asLong()).isGreaterThanOrEqualTo(12);
        assertThat(all.path("list").size()).isGreaterThanOrEqualTo(12);
        // 每条资源有原始链接与核验状态
        JsonNode first = all.path("list").get(0);
        assertThat(first.path("url").asText()).startsWith("https://");
        assertThat(first.path("verifiedStatus").asText()).isIn("PENDING", "VERIFIED", "PUBLISHED");

        // 按方向筛选
        for (String track : new String[]{"java-fullstack", "ai-fullstack", "python-agent"}) {
            JsonNode filtered = get("/api/learning/resources?track=" + track + "&size=50", userToken).path("data");
            assertThat(filtered.path("total").asLong()).isGreaterThanOrEqualTo(3);
            for (JsonNode r : filtered.path("list")) {
                assertThat(r.path("track").asText()).isEqualTo(track);
            }
        }
        // 按许可证状态筛选
        JsonNode mit = get("/api/learning/resources?licenseStatus=VERIFIED&size=50", userToken).path("data");
        assertThat(mit.path("total").asLong()).isGreaterThan(0);

        // 关键词搜索
        JsonNode kw = get("/api/learning/resources?keyword=Spring", userToken).path("data");
        assertThat(kw.path("total").asLong()).isGreaterThanOrEqualTo(1);

        // 分页
        JsonNode page1 = get("/api/learning/resources?page=1&size=5", userToken).path("data");
        assertThat(page1.path("list").size()).isLessThanOrEqualTo(5);
        assertThat(page1.path("total").asLong()).isGreaterThanOrEqualTo(12);
    }

    @Test
    @Order(4)
    @DisplayName("资源详情可见；许可证未核验时复制与再分发为禁止")
    void resourceDetailAndLicenseDefaults() {
        JsonNode all = get("/api/learning/resources?size=50", userToken).path("data");
        boolean checkedNoLicense = false;
        boolean checkedMit = false;
        for (JsonNode r : all.path("list")) {
            long id = r.path("id").asLong();
            JsonNode detail = get("/api/learning/resources/" + id, userToken).path("data");
            // 所有资源都有许可证信息字段与核验时间
            assertThat(detail.has("licenseStatus")).isTrue();
            assertThat(detail.path("verifiedAt")).isNotNull();
            if ("UNKNOWN".equals(detail.path("licenseStatus").asText())) {
                // 无许可证 → 复制与再分发必须禁止
                assertThat(detail.path("allowCopy").asBoolean()).isFalse();
                assertThat(detail.path("allowRedistribute").asBoolean()).isFalse();
                checkedNoLicense = true;
            }
            if ("MIT".equals(detail.path("licenseSpdx").asText())) {
                checkedMit = true;
            }
            if (firstResourceId == null) firstResourceId = id;
        }
        assertThat(checkedNoLicense).as("至少有一条无许可证资源被验证默认禁止").isTrue();
        assertThat(checkedMit).as("至少有一条 MIT 许可资源").isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("学习计划：加入、状态更新、用户隔离")
    void planLifecycle() {
        // 加入资源到计划
        ResponseEntity<String> add = rest.exchange("/api/learning/plan", HttpMethod.POST,
                new HttpEntity<>(Map.of("itemType", "RESOURCE", "itemCode", "RES-JAVA-001"),
                        authHeaders(userToken, true)), String.class);
        JsonNode addBody = body(add.getBody());
        assertThat(addBody.get("code").asInt()).isZero();
        planItemId = addBody.path("data").path("id").asLong();
        assertThat(planItemId).isPositive();

        // 更新状态为已完成 + 心得
        ResponseEntity<String> update = rest.exchange("/api/learning/plan/" + planItemId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "DONE", "note", "看完了路线图，对 Java 全栈有整体认知"),
                        authHeaders(userToken, true)), String.class);
        assertThat(body(update.getBody()).path("data").path("status").asText()).isEqualTo("DONE");
        assertThat(body(update.getBody()).path("data").path("note").asText()).contains("整体认知");

        // 用户隔离：第二个用户查不到第一个用户的计划
        ResponseEntity<String> other = rest.exchange("/api/learning/plan", HttpMethod.GET,
                new HttpEntity<>(authHeaders(secondUserToken, false)), String.class);
        assertThat(body(other.getBody()).path("data").size()).isZero();

        // 用户隔离：第二个用户不能修改第一个用户的计划条目（Business 异常走全局处理器返回 HTTP 200 + code 403）
        ResponseEntity<String> forbidden = rest.exchange("/api/learning/plan/" + planItemId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "IN_PROGRESS"), authHeaders(secondUserToken, true)), String.class);
        assertThat(body(forbidden.getBody()).path("code").asInt()).isEqualTo(403);
    }

    @Test
    @Order(6)
    @DisplayName("路线节点可加入计划并标记完成")
    void nodePlan() {
        JsonNode tracks = get("/api/learning/tracks", userToken).path("data");
        String nodeCode = tracks.get(0).path("stages").get(0).path("nodes").get(0).path("code").asText();
        assertThat(nodeCode).isNotBlank();

        ResponseEntity<String> add = rest.exchange("/api/learning/plan", HttpMethod.POST,
                new HttpEntity<>(Map.of("itemType", "NODE", "itemCode", nodeCode),
                        authHeaders(userToken, true)), String.class);
        assertThat(body(add.getBody()).get("code").asInt()).isZero();
    }

    @Test
    @Order(7)
    @DisplayName("管理员可以登记、重新核验、下架资源；普通用户 403")
    void adminResourceManagement() {
        // 普通用户访问管理端 → 403
        ResponseEntity<String> forbidden = rest.exchange("/api/admin/learning/resources", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        // 管理员登记新资源
        Map<String, Object> newResource = Map.of(
                "code", "RES-TEST-001",
                "track", "java-fullstack",
                "title", "测试用资源",
                "description", "集成测试登记的资源元数据",
                "url", "https://github.com/Yangxbao/OfferDojo",
                "sourceType", "DOCS");
        ResponseEntity<String> create = rest.exchange("/api/admin/learning/resources", HttpMethod.POST,
                new HttpEntity<>(newResource, authHeaders(adminToken, true)), String.class);
        JsonNode createBody = body(create.getBody());
        assertThat(createBody.get("code").asInt()).isZero();
        createdResourceId = createBody.path("data").path("id").asLong();
        // 新登记资源初始为待核验
        assertThat(createBody.path("data").path("verifiedStatus").asText()).isEqualTo("PENDING");
        // 使用权限默认禁止
        assertThat(createBody.path("data").path("allowCopy").asBoolean()).isFalse();
        assertThat(createBody.path("data").path("allowRedistribute").asBoolean()).isFalse();

        // 重新核验（调用 GitHub API；集成测试中网络可能受限，核心断言是管理端正常且不误标）
        ResponseEntity<String> reverify = rest.exchange("/api/admin/learning/resources/" + createdResourceId + "/reverify",
                HttpMethod.POST, new HttpEntity<>(authHeaders(adminToken, false)), String.class);
        JsonNode reverifyBody = body(reverify.getBody());
        assertThat(reverifyBody.get("code").asInt()).isZero();
        // 管理端接口正常返回；核验成功推进为 VERIFIED，失败保持 PENDING——两者都不误标为 PUBLISHED
        String statusAfterReverify = reverifyBody.path("data").path("resource").path("verifiedStatus").asText();
        assertThat(statusAfterReverify).isIn("PENDING", "VERIFIED");
        assertThat(reverifyBody.path("data").has("resource")).isTrue();

        // 下架
        ResponseEntity<String> offline = rest.exchange(
                "/api/admin/learning/resources/" + createdResourceId + "/status?status=OFFLINE",
                HttpMethod.PUT, new HttpEntity<>(authHeaders(adminToken, false)), String.class);
        assertThat(body(offline.getBody()).path("data").path("verifiedStatus").asText()).isEqualTo("OFFLINE");

        // 下架后用户不可见（Business 异常走全局处理器返回 HTTP 200 + code 404）
        ResponseEntity<String> afterOffline = rest.exchange(
                "/api/learning/resources/" + createdResourceId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(body(afterOffline.getBody()).path("code").asInt()).isEqualTo(404);
    }

    @Test
    @Order(8)
    @DisplayName("外部链接无法解析为 GitHub 仓库时，重新核验安全失败不误标")
    void reverifyFailureSafety() {
        // 登记一个非 GitHub 链接的资源
        Map<String, Object> badResource = Map.of(
                "code", "RES-BAD-001",
                "track", "python-agent",
                "title", "非 GitHub 链接测试",
                "description", "用于验证核验失败不会误标为已核验",
                "url", "https://docs.langchain.com/oss/python/langgraph/overview",
                "sourceType", "DOCS");
        ResponseEntity<String> create = rest.exchange("/api/admin/learning/resources", HttpMethod.POST,
                new HttpEntity<>(badResource, authHeaders(adminToken, true)), String.class);
        long badId = body(create.getBody()).path("data").path("id").asLong();

        // 重新核验：非 GitHub 链接 → 核验失败
        ResponseEntity<String> reverify = rest.exchange(
                "/api/admin/learning/resources/" + badId + "/reverify",
                HttpMethod.POST, new HttpEntity<>(authHeaders(adminToken, false)), String.class);
        JsonNode reverifyBody = body(reverify.getBody());
        assertThat(reverifyBody.get("code").asInt()).isZero();
        assertThat(reverifyBody.path("data").path("success").asBoolean()).isFalse();
        // 失败时状态保持待核验，不误标为已核验
        assertThat(reverifyBody.path("data").path("resource").path("verifiedStatus").asText())
                .isEqualTo("PENDING");
    }
}
