package com.jobsim.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobsim.config.AiProperties;
import com.jobsim.dto.task.RubricItem;
import com.jobsim.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 远程模型评估实现：调用 OpenAI 兼容的 /chat/completions 接口。
 *
 * 设计要点：
 * - 使用 JDK HttpClient（超时可控，依赖少，逻辑一目了然）
 * - 强制要求模型输出 JSON，并逐字段校验/钳制（分数 0-10、维度对齐评分标准）
 * - 任何失败都抛 AiServiceException，由 EvaluationService 统一降级为 Mock 并记录失败日志
 * - API Key 只出现在请求头，绝不进入日志与异常信息
 */
@Slf4j
public class RemoteAiProvider implements AiProvider {

    public static final String NAME = "REMOTE";

    private final AiProperties props;
    private final HttpClient httpClient;

    public RemoteAiProvider(AiProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext ctx) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(ctx)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AiServiceException(AiServiceException.TIMEOUT,
                    "调用模型超时（>%ds），请稍后重试".formatted(props.getTimeoutSeconds()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServiceException(AiServiceException.UNKNOWN, "请求被中断");
        } catch (IOException e) {
            throw new AiServiceException(AiServiceException.HTTP_ERROR, "网络错误，无法连接模型服务");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // 只保留响应片段用于排查，避免日志膨胀；不含任何密钥信息
            String snippet = response.body() == null ? "" : response.body().substring(0, Math.min(150, response.body().length()));
            log.warn("模型服务返回 HTTP {}: {}", response.statusCode(), snippet);
            throw new AiServiceException(AiServiceException.HTTP_ERROR,
                    "模型服务返回 HTTP " + response.statusCode());
        }

        return parseResult(extractContent(response.body()), ctx);
    }

    private String buildRequestBody(EvaluationContext ctx) {
        StringBuilder user = new StringBuilder();
        user.append("【任务】").append(ctx.title()).append("（编码 ").append(ctx.taskCode()).append("）\n");
        user.append("【任务背景】").append(ctx.background()).append("\n");
        user.append("【业务需求】\n");
        ctx.requirements().forEach(r -> user.append("- ").append(r).append("\n"));
        if (!ctx.constraints().isEmpty()) {
            user.append("【约束条件】\n");
            ctx.constraints().forEach(r -> user.append("- ").append(r).append("\n"));
        }
        if (!ctx.deliverables().isEmpty()) {
            user.append("【预期交付物】\n");
            ctx.deliverables().forEach(r -> user.append("- ").append(r).append("\n"));
        }
        if (!ctx.acceptance().isEmpty()) {
            user.append("【验收标准】\n");
            ctx.acceptance().forEach(r -> user.append("- ").append(r).append("\n"));
        }
        user.append("【评分标准】（dimension 必须与这里的维度完全一致）\n");
        ctx.rubric().forEach(r -> user.append("- ").append(r.dimension())
                .append("（权重 ").append(r.weight()).append("%）：").append(r.guidance()).append("\n"));
        user.append("【我的答案】\n").append(ctx.answer()).append("\n\n");
        user.append("""
                请作为严格的 Java 后端技术导师评估以上答案，只输出 JSON，不要输出任何其他文字。JSON 结构：
                {"dimension_scores":[{"dimension":"维度名","score":0到10的数字,"comment":"一句话评语"}],
                 "strengths":["正确做法1","正确做法2"],
                 "problems":["具体问题1","具体问题2"],
                 "edge_cases":["可能遗漏的边界情况"],
                 "suggestions":["改进建议"],
                 "follow_up_questions":["进一步的技术追问"],
                 "next_task_codes":["建议下一步练习的任务编码，只能从这些编码中选择：%s"]}
                """.formatted(String.join(",", ctx.allTaskCodes())));

        // 手工拼 JSON 有转义风险，用 Jackson 构建请求体
        var body = JsonUtils.MAPPER.createObjectNode();
        body.put("model", props.getModel());
        body.put("temperature", 0.2);
        if (props.isJsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content",
                "你是一名严格的 Java 后端技术导师，负责评估大学生在模拟岗位任务中的答案。输出必须是合法 JSON。");
        messages.addObject().put("role", "user").put("content", user.toString());
        return JsonUtils.toJson(body);
    }

    /** 从 chat.completions 响应中取出 message.content */
    private String extractContent(String responseBody) {
        JsonNode root = JsonUtils.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new AiServiceException(AiServiceException.PARSE_ERROR, "模型响应中没有内容");
        }
        return content.asText();
    }

    private EvaluationResult parseResult(String content, EvaluationContext ctx) {
        JsonNode root;
        try {
            root = JsonUtils.readTree(stripFences(content));
        } catch (Exception e) {
            throw new AiServiceException(AiServiceException.PARSE_ERROR, "模型输出不是合法 JSON");
        }

        List<EvaluationResult.DimensionScore> dims = new ArrayList<>();
        JsonNode scoreNodes = root.path("dimension_scores");
        for (RubricItem item : ctx.rubric()) {
            JsonNode node = findDimension(scoreNodes, item.dimension());
            double score = node == null ? 5.0 : clamp(node.path("score").asDouble(5.0));
            String comment = node == null
                    ? "模型未给出该维度评分，已按中性分处理"
                    : node.path("comment").asText("");
            dims.add(new EvaluationResult.DimensionScore(item.dimension(),
                    Math.round(score * 10) / 10.0, item.weight(), comment));
        }

        return new EvaluationResult(
                dims,
                readStrings(root, "strengths"),
                readStrings(root, "problems"),
                readStrings(root, "edge_cases"),
                readStrings(root, "suggestions"),
                readStrings(root, "follow_up_questions"),
                readStrings(root, "next_task_codes").stream()
                        .filter(ctx.allTaskCodes()::contains)
                        .toList());
    }

    private JsonNode findDimension(JsonNode scoreNodes, String dimension) {
        if (!scoreNodes.isArray()) {
            return null;
        }
        for (JsonNode node : scoreNodes) {
            if (dimension.equals(node.path("dimension").asText())) {
                return node;
            }
        }
        return null;
    }

    private List<String> readStrings(JsonNode root, String field) {
        JsonNode node = root.path(field);
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> {
                String s = n.asText("");
                if (!s.isBlank()) {
                    result.add(s);
                }
            });
        }
        return result;
    }

    private double clamp(double score) {
        return Math.min(10, Math.max(0, score));
    }

    /** 去掉部分模型喜欢包裹的 ```json ... ``` 围栏 */
    private String stripFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > 0) {
                trimmed = trimmed.substring(firstLineEnd + 1);
            }
            int fenceEnd = trimmed.lastIndexOf("```");
            if (fenceEnd >= 0) {
                trimmed = trimmed.substring(0, fenceEnd);
            }
        }
        return trimmed.trim();
    }
}
