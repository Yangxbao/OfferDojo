package com.jobsim.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.common.PageResult;
import com.jobsim.dto.admin.AdminOverviewVO;
import com.jobsim.dto.admin.AiLogVO;
import com.jobsim.entity.AiCallLog;
import com.jobsim.entity.Evaluation;
import com.jobsim.entity.Submission;
import com.jobsim.entity.Task;
import com.jobsim.entity.User;
import com.jobsim.mapper.AiCallLogMapper;
import com.jobsim.mapper.EvaluationMapper;
import com.jobsim.mapper.SubmissionMapper;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.mapper.UserMapper;
import com.jobsim.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 后台管理服务：总览统计、评估记录、AI 调用日志（含失败记录）。
 * 任务的新建/编辑/下架在 TaskService 中（adminPage/create/update/updateStatus）。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;
    private final EvaluationMapper evaluationMapper;
    private final AiCallLogMapper aiCallLogMapper;

    public AdminOverviewVO overview() {
        long userCount = userMapper.selectCount(null);
        long totalTasks = taskMapper.selectCount(null);
        long publishedTasks = taskMapper.selectCount(Wrappers.<Task>lambdaQuery().eq(Task::getStatus, TaskService.STATUS_PUBLISHED));
        long submissionCount = submissionMapper.selectCount(
                Wrappers.<Submission>lambdaQuery().eq(Submission::getStatus, Submission.STATUS_EVALUATED));
        long evaluationCount = evaluationMapper.selectCount(null);
        Double avgScore = evaluationMapper.selectAvgScore();
        long failure24h = aiCallLogMapper.selectCount(Wrappers.<AiCallLog>lambdaQuery()
                .eq(AiCallLog::getSuccess, false)
                .ge(AiCallLog::getCreatedAt, LocalDateTime.now().minusHours(24)));
        return new AdminOverviewVO(userCount, publishedTasks, totalTasks,
                submissionCount, evaluationCount,
                avgScore == null ? 0 : avgScore, failure24h);
    }

    public PageResult<EvaluationMapper.EvaluationRow> evaluationPage(Long taskId, Long page, Long size) {
        IPage<EvaluationMapper.EvaluationRow> result =
                evaluationMapper.selectAdminPage(PageUtils.of(page, size), taskId);
        return PageResult.from(result);
    }

    public PageResult<AiLogVO> aiLogPage(Boolean success, Long page, Long size) {
        IPage<AiCallLog> result = aiCallLogMapper.selectPage(PageUtils.of(page, size),
                Wrappers.<AiCallLog>lambdaQuery()
                        .eq(success != null, AiCallLog::getSuccess, success)
                        .orderByDesc(AiCallLog::getCreatedAt));
        return PageResult.map(result, l -> new AiLogVO(l.getId(), l.getProvider(), l.getModel(),
                l.getTaskCode(), Boolean.TRUE.equals(l.getSuccess()), l.getErrorType(),
                l.getMessage(), l.getDurationMs(), l.getCreatedAt()));
    }
}
