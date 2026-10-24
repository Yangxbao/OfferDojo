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
 * 完整业务流程集成测试（H2 内存库，覆盖真实 HTTP + Security + MyBatis-Plus 链路）：
 * 注册 → 登录 → 浏览任务 → 保存草稿 → 提交并评估 → 工作台/能力分析 → 权限校验 → 管理端。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;

    private static String userToken;
    private static String adminToken;
    private static Long firstTaskId;
    private static Long draftId;
    private static Long submissionId;
    private static Long evaluationId;

    private HttpHeaders authHeaders(String token, boolean json) {
        HttpHeaders headers = new HttpHeaders();
        if (json) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private JsonNode body(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @Order(1)
    @DisplayName("未登录访问受保护接口返回 401")
    void protectedEndpointRequiresAuth() {
        ResponseEntity<String> resp = rest.getForEntity("/api/tasks", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @Order(2)
    @DisplayName("注册 → 登录 → 获取当前用户")
    void registerLoginAndMe() {
        var headers = authHeaders(null, true);
        ResponseEntity<String> reg = rest.postForEntity("/api/auth/register",
                new HttpEntity<>(Map.of("username", "flowuser", "password", "pass1234", "nickname", "流程测试"), headers),
                String.class);
        JsonNode regBody = body(reg.getBody());
        assertThat(regBody.get("code").asInt()).isZero();
        userToken = regBody.path("data").path("token").asText();
        assertThat(userToken).isNotBlank();

        ResponseEntity<String> me = rest.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(body(me.getBody()).path("data").path("username").asText()).isEqualTo("flowuser");
    }

    @Test
    @Order(3)
    @DisplayName("内置任务列表与详情可用")
    void listAndDetailTasks() {
        // 一页拿全量（默认分页大小为 10，这里显式放大）
        ResponseEntity<String> list = rest.exchange("/api/tasks?size=20", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        JsonNode listBody = body(list.getBody());
        assertThat(listBody.get("code").asInt()).isZero();
        assertThat(listBody.path("data").path("total").asLong()).isGreaterThanOrEqualTo(12);
        JsonNode tasks = listBody.path("data").path("list");
        assertThat(tasks.size()).isGreaterThanOrEqualTo(12);
        firstTaskId = tasks.get(0).path("id").asLong();
        assertThat(tasks.get(0).path("myStatus").asText()).isEqualTo("NOT_STARTED");

        ResponseEntity<String> detail = rest.exchange("/api/tasks/" + firstTaskId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        JsonNode detailBody = body(detail.getBody());
        assertThat(detailBody.path("data").path("background").asText()).isNotBlank();
        assertThat(detailBody.path("data").path("rubric").size()).isGreaterThan(0);
    }

    @Test
    @Order(4)
    @DisplayName("保存草稿后可继续更新（覆盖式）")
    void saveDraftTwice() {
        var headers = authHeaders(userToken, true);
        ResponseEntity<String> first = rest.exchange("/api/tasks/" + firstTaskId + "/draft", HttpMethod.PUT,
                new HttpEntity<>(Map.of("content", "第一版草稿"), headers), String.class);
        draftId = body(first.getBody()).path("data").asLong();
        assertThat(draftId).isPositive();

        ResponseEntity<String> second = rest.exchange("/api/tasks/" + firstTaskId + "/draft", HttpMethod.PUT,
                new HttpEntity<>(Map.of("content", "第二版草稿，内容更完整"), headers), String.class);
        assertThat(body(second.getBody()).path("data").asLong()).isEqualTo(draftId);

        ResponseEntity<String> detail = rest.exchange("/api/tasks/" + firstTaskId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(body(detail.getBody()).path("data").path("myDraftContent").asText())
                .isEqualTo("第二版草稿，内容更完整");
        assertThat(body(detail.getBody()).path("data").path("myStatus").asText()).isEqualTo("DRAFTING");
    }

    @Test
    @Order(5)
    @DisplayName("提交答案触发 AI 评估，返回结构化报告")
    void submitAndEvaluate() {
        String answer = """
                ## 资源划分
                涉及签到（sign-in）资源，老师与学生两类角色。
                ## 接口清单
                1. POST /api/courses/{courseId}/sign-ins —— 老师发起签到，服务端用当前时间加 5 分钟作为截止时间
                2. POST /api/sign-ins/{id} —— 学生签到，截止后返回 400 与明确错误码
                3. PUT /api/sign-ins/{id}/late —— 老师补签，记录操作人
                4. GET /api/me/courses/{courseId}/sign-ins —— 学生查询签到记录
                ## 状态机
                状态枚举：NORMAL / LATE / ABSENT / COMPENSATED。截止时间之后签到请求返回 400；
                补签把状态改为已补签并记录老师 id。
                响应使用统一结构，错误码区分 401 未登录、403 无权限、409 重复签到。
                """;
        var headers = authHeaders(userToken, true);
        ResponseEntity<String> resp = rest.exchange("/api/tasks/" + firstTaskId + "/submit", HttpMethod.POST,
                new HttpEntity<>(Map.of("content", answer), headers), String.class);
        JsonNode respBody = body(resp.getBody());
        assertThat(respBody.get("code").asInt()).isZero();
        JsonNode data = respBody.path("data");
        assertThat(data.path("status").asText()).isEqualTo("EVALUATED");
        submissionId = data.path("id").asLong();

        JsonNode evaluation = data.path("evaluation");
        assertThat(evaluation.path("totalScore").asDouble()).isStrictlyBetween(0.0, 10.0);
        assertThat(evaluation.path("provider").asText()).isEqualTo("MOCK");
        assertThat(evaluation.path("notice").asText()).contains("模拟评估");
        assertThat(evaluation.path("dimensionScores").size()).isGreaterThan(0);
        assertThat(evaluation.path("strengths").size()).isGreaterThan(0);
        assertThat(evaluation.path("nextTasks").size()).isGreaterThan(0);
        evaluationId = evaluation.path("id").asLong();
    }

    @Test
    @Order(6)
    @DisplayName("工作台与能力分析反映真实提交数据")
    void dashboardAndProgress() {
        ResponseEntity<String> dash = rest.exchange("/api/dashboard", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        JsonNode dashBody = body(dash.getBody());
        assertThat(dashBody.path("data").path("completedTasks").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(dashBody.path("data").path("abilities").size()).isEqualTo(6);
        assertThat(dashBody.path("data").path("recent").size()).isGreaterThanOrEqualTo(1);
        assertThat(dashBody.path("data").path("todayTask").path("id").asLong()).isPositive();

        ResponseEntity<String> progress = rest.exchange("/api/my/progress", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        JsonNode progressBody = body(progress.getBody());
        assertThat(progressBody.path("data").path("completion").path("completedTasks").asLong())
                .isGreaterThanOrEqualTo(1);
        assertThat(progressBody.path("data").path("completion").path("rate").asDouble()).isPositive();
    }

    @Test
    @Order(7)
    @DisplayName("历史记录与详情可查，且不能越权访问他人提交")
    void historyAndOwnership() {
        ResponseEntity<String> history = rest.exchange("/api/my/submissions", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(body(history.getBody()).path("data").path("total").asLong()).isGreaterThanOrEqualTo(1);

        ResponseEntity<String> detail = rest.exchange("/api/my/submissions/" + submissionId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(body(detail.getBody()).path("data").path("content").asText()).contains("资源划分");

        // 普通用户访问管理接口被拒
        ResponseEntity<String> admin = rest.exchange("/api/admin/overview", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(admin.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @Order(8)
    @DisplayName("管理员登录后可以访问后台统计与评估记录")
    void adminAccess() {
        var headers = authHeaders(null, true);
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("username", "admin", "password", "admin123"), headers), String.class);
        adminToken = body(login.getBody()).path("data").path("token").asText();
        assertThat(adminToken).isNotBlank();

        ResponseEntity<String> overview = rest.exchange("/api/admin/overview", HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken, false)), String.class);
        JsonNode overviewBody = body(overview.getBody());
        assertThat(overviewBody.path("data").path("userCount").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(overviewBody.path("data").path("publishedTasks").asLong()).isGreaterThanOrEqualTo(12);

        ResponseEntity<String> evals = rest.exchange("/api/admin/evaluations", HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken, false)), String.class);
        assertThat(body(evals.getBody()).path("data").path("total").asLong()).isGreaterThanOrEqualTo(1);

        // 错误密码登录被拒
        ResponseEntity<String> bad = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("username", "admin", "password", "wrong-password"), headers), String.class);
        assertThat(body(bad.getBody()).path("code").asInt()).isEqualTo(400);
    }

    @Test
    @Order(9)
    @DisplayName("学习路线图与八股复习区可用")
    void roadmapAndBagu() {
        ResponseEntity<String> roadmap = rest.exchange("/api/roadmap", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        JsonNode stages = body(roadmap.getBody()).path("data");
        assertThat(stages.size()).isGreaterThanOrEqualTo(10);
        assertThat(stages.get(0).path("tasks").size()).isGreaterThan(0);
        // 路线图任务带当前用户进度
        assertThat(stages.get(0).path("tasks").get(0).has("myStatus")).isTrue();

        ResponseEntity<String> bagu = rest.exchange("/api/bagu/questions?size=50", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        JsonNode baguBody = body(bagu.getBody());
        assertThat(baguBody.path("data").path("total").asLong()).isGreaterThanOrEqualTo(70);
        assertThat(baguBody.path("data").path("list").get(0).path("oneLiner").asText()).isNotBlank();

        ResponseEntity<String> cats = rest.exchange("/api/bagu/categories", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(body(cats.getBody()).path("data").size()).isGreaterThan(0);

        // 高频筛选
        ResponseEntity<String> high = rest.exchange("/api/bagu/questions?frequency=HIGH&size=1", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(body(high.getBody()).path("data").path("total").asLong()).isGreaterThan(10);
    }

    @Test
    @Order(10)
    @DisplayName("追问模拟面试：回答追问获得反馈并沉淀对话")
    void followupDialog() {
        assertThat(evaluationId).isPositive();
        var headers = authHeaders(userToken, true);
        // 从评估报告里取一个追问来回答（没有追问就用兜底问题）
        ResponseEntity<String> detail = rest.exchange("/api/my/submissions/" + submissionId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        JsonNode followUps = body(detail.getBody()).path("data").path("evaluation").path("followUps");
        String question = followUps.size() > 0 ? followUps.get(0).asText() : "为什么这么做？";

        ResponseEntity<String> answer = rest.exchange("/api/my/evaluations/" + evaluationId + "/dialog",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("question", question, "content",
                        "因为服务端时间是唯一可信的时间源，客户端时间可以被篡改，" +
                        "所以签到截止判断必须用服务端时间，并在接口层做边界校验。"), headers), String.class);
        JsonNode answerBody = body(answer.getBody());
        assertThat(answerBody.get("code").asInt()).isZero();
        assertThat(answerBody.path("data").path("score").asDouble()).isStrictlyBetween(0.0, 10.0);
        assertThat(answerBody.path("data").path("feedback").asText()).isNotBlank();

        // 对话记录沉淀
        ResponseEntity<String> list = rest.exchange("/api/my/evaluations/" + evaluationId + "/dialog",
                HttpMethod.GET, new HttpEntity<>(authHeaders(userToken, false)), String.class);
        assertThat(body(list.getBody()).path("data").size()).isEqualTo(1);
    }

    @Test
    @Order(11)
    @DisplayName("简历素材与 JD 反向匹配")
    void careerFeatures() {
        ResponseEntity<String> resume = rest.exchange("/api/my/resume-material", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken, false)), String.class);
        JsonNode resumeBody = body(resume.getBody());
        assertThat(resumeBody.get("code").asInt()).isZero();
        assertThat(resumeBody.path("data").path("markdown").asText()).contains("简历素材草稿");
        assertThat(resumeBody.path("data").path("completedTasks").asLong()).isGreaterThanOrEqualTo(1);

        ResponseEntity<String> match = rest.exchange("/api/jd/match", HttpMethod.POST,
                new HttpEntity<>(Map.of("text", "岗位要求：熟悉 MySQL 索引优化与事务，了解 Redis 缓存，"
                        + "掌握 Spring Boot，有高并发经验"), authHeaders(userToken, true)), String.class);
        JsonNode matchBody = body(match.getBody());
        assertThat(matchBody.get("code").asInt()).isZero();
        assertThat(matchBody.path("data").path("totalTaskHits").asLong()).isGreaterThanOrEqualTo(1);
    }
}
