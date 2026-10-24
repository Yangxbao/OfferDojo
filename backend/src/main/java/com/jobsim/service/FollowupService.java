package com.jobsim.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.common.BusinessException;
import com.jobsim.common.ErrorCode;
import com.jobsim.dto.followup.FollowupDialogVO;
import com.jobsim.entity.Evaluation;
import com.jobsim.entity.FollowupDialog;
import com.jobsim.entity.Submission;
import com.jobsim.entity.Task;
import com.jobsim.mapper.EvaluationMapper;
import com.jobsim.mapper.FollowupDialogMapper;
import com.jobsim.mapper.SubmissionMapper;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.service.ai.AiProvider;
import com.jobsim.service.ai.AiServiceException;
import com.jobsim.service.ai.FollowupContext;
import com.jobsim.service.ai.FollowupFeedback;
import com.jobsim.service.ai.MockAiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 追问模拟面试服务：把评估报告中的"技术追问"变成可作答的多轮对话。
 * 每次回答由主 Provider 评估，远程失败自动降级 Mock（与主评估同策略），
 * 归属校验（只能操作自己的评估）与主评估服务保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowupService {

    private final FollowupDialogMapper dialogMapper;
    private final EvaluationMapper evaluationMapper;
    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final AiProvider aiProvider;
    private final MockAiProvider mockProvider;

    public List<FollowupDialogVO> list(Long userId, Long evaluationId) {
        requireOwnedEvaluation(userId, evaluationId);
        return dialogMapper.selectList(Wrappers.<FollowupDialog>lambdaQuery()
                        .eq(FollowupDialog::getEvaluationId, evaluationId)
                        .orderByAsc(FollowupDialog::getId))
                .stream().map(this::toVO).toList();
    }

    public FollowupDialogVO answer(Long userId, Long evaluationId, String question, String answer) {
        Evaluation evaluation = requireOwnedEvaluation(userId, evaluationId);
        Task task = taskMapper.selectById(evaluation.getTaskId());
        Submission submission = submissionMapper.selectById(evaluation.getSubmissionId());

        // 多轮上下文：同一评估下的历史对话（问/答/反馈）
        List<FollowupDialog> history = dialogMapper.selectList(Wrappers.<FollowupDialog>lambdaQuery()
                .eq(FollowupDialog::getEvaluationId, evaluationId)
                .orderByAsc(FollowupDialog::getId));

        FollowupContext context = new FollowupContext(
                task == null ? "" : task.getTitle(),
                question,
                answer,
                excerpt(submission == null ? "" : submission.getContent()),
                history.stream()
                        .map(h -> new FollowupContext.HistoryItem(h.getQuestion(), h.getAnswer(), h.getFeedback()))
                        .toList());

        FollowupFeedback feedback;
        String provider;
        String notice = null;
        if (aiProvider instanceof com.jobsim.service.ai.RemoteAiProvider) {
            long start = System.currentTimeMillis();
            try {
                feedback = aiProvider.followUp(context);
                provider = "REMOTE";
            } catch (AiServiceException e) {
                log.warn("远程追问评估失败（{}），已降级为本地模拟：{}", e.getErrorType(), e.getMessage());
                feedback = mockProvider.followUp(context);
                provider = "MOCK_FALLBACK";
                notice = "远程模型调用失败（%s），本次反馈由本地模拟生成，仅供参考".formatted(e.getErrorType());
            }
        } else {
            feedback = aiProvider.followUp(context);
            provider = "MOCK";
            notice = "本地模拟反馈（未配置真实模型），仅供参考";
        }

        FollowupDialog dialog = new FollowupDialog();
        dialog.setEvaluationId(evaluationId);
        dialog.setUserId(userId);
        dialog.setQuestion(question);
        dialog.setAnswer(answer);
        dialog.setScore(BigDecimal.valueOf(feedback.score()));
        dialog.setFeedback(feedback.comment() + (feedback.suggestion() == null || feedback.suggestion().isBlank()
                ? "" : "\n建议：" + feedback.suggestion()));
        dialog.setProvider(provider);
        dialog.setNotice(notice);
        dialogMapper.insert(dialog);
        return toVO(dialog);
    }

    private Evaluation requireOwnedEvaluation(Long userId, Long evaluationId) {
        Evaluation evaluation = evaluationMapper.selectById(evaluationId);
        if (evaluation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评估记录不存在");
        }
        if (!evaluation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己的评估记录");
        }
        return evaluation;
    }

    /** 原始答案摘要（追问需要结合原始答案上下文，避免答非所问） */
    private String excerpt(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 800 ? content.substring(0, 800) + "…" : content;
    }

    private FollowupDialogVO toVO(FollowupDialog d) {
        return new FollowupDialogVO(d.getId(), d.getEvaluationId(), d.getQuestion(), d.getAnswer(),
                d.getScore() == null ? 0 : d.getScore().doubleValue(), d.getFeedback(),
                d.getProvider(), d.getNotice(), d.getCreatedAt());
    }
}
