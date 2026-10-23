package com.jobsim.dto.dashboard;

import com.jobsim.dto.task.TaskVO;
import com.jobsim.dto.submission.SubmissionVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作台数据。
 * 所有统计都基于当前用户在数据库中的真实提交/评估记录计算，没有写死的数字。
 */
public record DashboardVO(
        TaskVO todayTask,
        String todayReason,
        long completedTasks,
        long publishedTasks,
        List<com.jobsim.dto.progress.AbilityVO> abilities,
        List<SubmissionVO> recent,
        List<TaskVO> recommended,
        ResumeVO resume
) {

    /** 继续上次任务入口（无草稿时为 null） */
    public record ResumeVO(Long submissionId, Long taskId, String taskTitle, LocalDateTime updatedAt) {
    }
}
