package com.jobsim.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.common.BusinessException;
import com.jobsim.common.ErrorCode;
import com.jobsim.common.PageResult;
import com.jobsim.dto.evaluation.DimensionScoreVO;
import com.jobsim.dto.evaluation.EvaluationVO;
import com.jobsim.dto.submission.SaveDraftRequest;
import com.jobsim.dto.submission.SubmitRequest;
import com.jobsim.dto.submission.SubmissionDetailVO;
import com.jobsim.dto.submission.SubmissionVO;
import com.jobsim.dto.task.TaskVO;
import com.jobsim.entity.Evaluation;
import com.jobsim.entity.Submission;
import com.jobsim.entity.Task;
import com.jobsim.mapper.EvaluationMapper;
import com.jobsim.mapper.SubmissionMapper;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.util.JsonUtils;
import com.jobsim.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 提交服务：保存草稿、提交并评估、历史记录与详情。
 * 权限控制：所有"我的数据"接口都以 userId 过滤或校验归属，防止越权访问他人数据。
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final EvaluationMapper evaluationMapper;
    private final EvaluationService evaluationService;
    private final TaskService taskService;

    /**
     * 提交答案并触发评估。
     * 如果存在草稿则把草稿升级为正式提交（保留同一条记录），否则新建提交记录。
     * 提交与评估在同一个事务里：评估写库失败则提交本身也不落库，保持一致。
     */
    @Transactional
    public SubmissionDetailVO submit(Long userId, Long taskId, SubmitRequest request) {
        Task task = taskService.requirePublished(taskId);
        Submission submission = latestDraft(userId, taskId);
        if (submission == null) {
            submission = new Submission();
            submission.setUserId(userId);
            submission.setTaskId(taskId);
        }
        submission.setContent(request.content());
        submission.setStatus(Submission.STATUS_EVALUATED);
        if (submission.getId() == null) {
            submissionMapper.insert(submission);
        } else {
            submissionMapper.updateById(submission);
        }

        List<String> allCodes = taskService.listPublished().stream().map(Task::getCode).toList();
        evaluationService.evaluate(submission, task, allCodes);
        return detail(userId, submission.getId());
    }

    /** 保存草稿：同一任务同一用户只保留最新一份草稿（覆盖式更新） */
    public Long saveDraft(Long userId, Long taskId, SaveDraftRequest request) {
        taskService.requirePublished(taskId);
        Submission draft = latestDraft(userId, taskId);
        if (draft == null) {
            draft = new Submission();
            draft.setUserId(userId);
            draft.setTaskId(taskId);
            draft.setStatus(Submission.STATUS_DRAFT);
            draft.setContent(request.content());
            submissionMapper.insert(draft);
        } else {
            draft.setContent(request.content());
            submissionMapper.updateById(draft);
        }
        return draft.getId();
    }

    /** 我的练习历史（仅已提交并评估的记录） */
    public PageResult<SubmissionVO> myHistory(Long userId, Long page, Long size) {
        IPage<SubmissionMapper.SubmissionRow> result =
                submissionMapper.selectMyHistory(PageUtils.of(page, size), userId);
        return PageResult.map(result, r -> new SubmissionVO(
                r.id(), r.taskId(), r.taskTitle(), r.taskCategory(), r.taskDifficulty(),
                r.status(), r.totalScore() == null ? null : r.totalScore().doubleValue(),
                r.createdAt(), r.updatedAt()));
    }

    public SubmissionDetailVO detail(Long userId, Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交记录不存在");
        }
        // 归属校验：用户只能查看自己的提交
        if (!submission.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看自己的提交记录");
        }
        Task task = taskMapper.selectById(submission.getTaskId());
        return new SubmissionDetailVO(
                submission.getId(),
                task == null ? null : task.getId(),
                task == null ? "已删除任务" : task.getTitle(),
                task == null ? null : task.getCategory(),
                task == null ? null : task.getDifficulty(),
                submission.getStatus(),
                submission.getContent(),
                submission.getUpdatedAt(),
                buildEvaluationVO(userId, submission, task));
    }

    private EvaluationVO buildEvaluationVO(Long userId, Submission submission, Task task) {
        Evaluation evaluation = evaluationMapper.selectOne(Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getSubmissionId, submission.getId()));
        if (evaluation == null) {
            return null; // 草稿没有评估
        }
        List<DimensionScoreVO> dims = new ArrayList<>();
        for (var d : JsonUtils.readList(evaluation.getDimensionScores(), EvaluationResultDim.class)) {
            dims.add(new DimensionScoreVO(d.dimension(), d.score(),
                    d.weight() == null ? 0 : d.weight(), d.comment()));
        }
        List<String> nextCodes = JsonUtils.readList(evaluation.getNextTaskCodes(), String.class);
        List<TaskVO> nextTasks = task == null ? List.of()
                : taskService.recommendNext(userId, task.getId(), nextCodes, 3);
        return new EvaluationVO(
                evaluation.getId(),
                evaluation.getTotalScore() == null ? 0 : evaluation.getTotalScore().doubleValue(),
                evaluation.getProvider(),
                evaluation.getModel(),
                evaluation.getNotice(),
                evaluation.getCreatedAt(),
                dims,
                JsonUtils.readList(evaluation.getStrengths(), String.class),
                JsonUtils.readList(evaluation.getProblems(), String.class),
                JsonUtils.readList(evaluation.getEdgeCases(), String.class),
                JsonUtils.readList(evaluation.getSuggestions(), String.class),
                JsonUtils.readList(evaluation.getFollowUps(), String.class),
                nextTasks);
    }

    private Submission latestDraft(Long userId, Long taskId) {
        return submissionMapper.selectOne(Wrappers.<Submission>lambdaQuery()
                .eq(Submission::getUserId, userId)
                .eq(Submission::getTaskId, taskId)
                .eq(Submission::getStatus, Submission.STATUS_DRAFT)
                .orderByDesc(Submission::getUpdatedAt)
                .last("LIMIT 1"));
    }

    /** dimension_scores JSON 的解析载体 */
    public record EvaluationResultDim(String dimension, double score, Integer weight, String comment) {
    }
}
