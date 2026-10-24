package com.jobsim.service;

import com.jobsim.config.AiProperties;
import com.jobsim.dto.task.RubricItem;
import com.jobsim.entity.AiCallLog;
import com.jobsim.entity.Evaluation;
import com.jobsim.entity.Submission;
import com.jobsim.entity.Task;
import com.jobsim.mapper.AiCallLogMapper;
import com.jobsim.mapper.EvaluationMapper;
import com.jobsim.service.ai.AiProvider;
import com.jobsim.service.ai.AiServiceException;
import com.jobsim.service.ai.EvaluationContext;
import com.jobsim.service.ai.EvaluationResult;
import com.jobsim.service.ai.MockAiProvider;
import com.jobsim.service.ai.MockHints;
import com.jobsim.service.ai.RemoteAiProvider;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评估编排服务：选择 Provider → 评估 → 远程失败时降级 Mock → 落库。
 *
 * 降级策略（真实系统同样适用）：
 * - 主 Provider 是 RemoteAiProvider 时，任何 AiServiceException（超时/HTTP 错误/解析失败）
 *   都会记录失败日志并回退到本地模拟评估，用户仍然能拿到结构化结果；
 * - 结果中 provider=MOCK_FALLBACK 且带 notice，前端会明确提示"本次为降级评估"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    public static final String PROVIDER_REMOTE = "REMOTE";
    public static final String PROVIDER_MOCK = "MOCK";
    public static final String PROVIDER_FALLBACK = "MOCK_FALLBACK";

    /** 评估提示词版本：prompt 或评分逻辑变更时递增，配合 raw_response 做追溯与 A/B 对比 */
    public static final String PROMPT_VERSION = "v1";

    private final AiProvider aiProvider;
    private final MockAiProvider mockProvider;
    private final EvaluationMapper evaluationMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AiProperties aiProperties;

    public Evaluation evaluate(Submission submission, Task task, List<String> allTaskCodes) {
        EvaluationContext ctx = buildContext(task, submission.getContent(), allTaskCodes);

        EvaluationResult result;
        String provider;
        String model = null;
        String notice;
        if (aiProvider instanceof RemoteAiProvider) {
            long start = System.currentTimeMillis();
            try {
                result = aiProvider.evaluate(ctx);
                provider = PROVIDER_REMOTE;
                model = aiProperties.getModel();
                notice = null;
                logCall(PROVIDER_REMOTE, model, task.getCode(), true, null, "评估成功",
                        (int) (System.currentTimeMillis() - start));
            } catch (AiServiceException e) {
                log.warn("远程模型评估失败（{}）：{}，已降级为本地模拟评估", e.getErrorType(), e.getMessage());
                logCall(PROVIDER_REMOTE, aiProperties.getModel(), task.getCode(), false,
                        e.getErrorType(), e.getMessage(), (int) (System.currentTimeMillis() - start));
                result = mockProvider.evaluate(ctx);
                provider = PROVIDER_FALLBACK;
                notice = "远程模型调用失败（%s），本次结果由本地模拟评估生成，仅供参考".formatted(e.getErrorType());
            }
        } else {
            result = aiProvider.evaluate(ctx);
            provider = PROVIDER_MOCK;
            notice = "本结果由内置模拟评估生成（未配置真实模型），用于演示完整流程，评分仅供参考";
        }

        return persist(submission, task, result, provider, model, notice);
    }

    private Evaluation persist(Submission submission, Task task, EvaluationResult result,
                               String provider, String model, String notice) {
        Evaluation evaluation = new Evaluation();
        evaluation.setSubmissionId(submission.getId());
        evaluation.setUserId(submission.getUserId());
        evaluation.setTaskId(task.getId());
        evaluation.setTotalScore(weightedTotal(result));
        evaluation.setDimensionScores(JsonUtils.toJson(result.dimensionScores()));
        evaluation.setStrengths(JsonUtils.toJson(result.strengths()));
        evaluation.setProblems(JsonUtils.toJson(result.problems()));
        evaluation.setEdgeCases(JsonUtils.toJson(result.edgeCases()));
        evaluation.setSuggestions(JsonUtils.toJson(result.suggestions()));
        evaluation.setFollowUps(JsonUtils.toJson(result.followUps()));
        evaluation.setNextTaskCodes(JsonUtils.toJson(result.nextTaskCodes()));
        evaluation.setProvider(provider);
        evaluation.setModel(model);
        evaluation.setNotice(notice);
        evaluation.setPromptVersion(PROMPT_VERSION);
        evaluation.setRawResponse(result.rawText());
        evaluationMapper.insert(evaluation);
        return evaluation;
    }

    /** 总分 = 各维度得分按权重的加权平均（保留一位小数） */
    private BigDecimal weightedTotal(EvaluationResult result) {
        double sum = 0;
        double weights = 0;
        for (EvaluationResult.DimensionScore d : result.dimensionScores()) {
            sum += d.score() * d.weight();
            weights += d.weight();
        }
        double total = weights == 0 ? 0 : sum / weights;
        return BigDecimal.valueOf(Math.round(total * 10) / 10.0);
    }

    private void logCall(String provider, String model, String taskCode,
                         boolean success, String errorType, String message, int durationMs) {
        try {
            AiCallLog logRow = new AiCallLog();
            logRow.setProvider(provider);
            logRow.setModel(model);
            logRow.setTaskCode(taskCode);
            logRow.setSuccess(success);
            logRow.setErrorType(errorType);
            logRow.setMessage(message);
            logRow.setDurationMs(durationMs);
            aiCallLogMapper.insert(logRow);
        } catch (Exception ex) {
            // 日志写入失败不影响主流程
            log.warn("AI 调用日志写入失败: {}", ex.getMessage());
        }
    }

    private EvaluationContext buildContext(Task task, String answer, List<String> allTaskCodes) {
        return new EvaluationContext(
                task.getCode(), task.getTitle(), task.getBackground(),
                JsonUtils.readList(task.getRequirements(), String.class),
                JsonUtils.readList(task.getConstraintItems(), String.class),
                JsonUtils.readList(task.getDeliverables(), String.class),
                JsonUtils.readList(task.getAcceptance(), String.class),
                JsonUtils.readList(task.getRubricJson(), RubricItem.class),
                answer,
                allTaskCodes,
                JsonUtils.fromJsonQuietly(task.getMockHintsJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {
                }));
    }
}
