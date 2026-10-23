package com.jobsim.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobsim.entity.Submission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SubmissionMapper extends BaseMapper<Submission> {

    /** 我的练习历史：联表取任务信息与评估得分 */
    @Select("""
            SELECT s.id, s.user_id AS userId, s.task_id AS taskId, s.status, s.content,
                   s.created_at AS createdAt, s.updated_at AS updatedAt,
                   t.title AS taskTitle, t.category AS taskCategory, t.difficulty AS taskDifficulty,
                   e.total_score AS totalScore
            FROM task_submission s
            JOIN task t ON t.id = s.task_id
            LEFT JOIN task_evaluation e ON e.submission_id = s.id
            WHERE s.user_id = #{userId} AND s.status = 'EVALUATED'
            ORDER BY s.updated_at DESC
            """)
    IPage<SubmissionRow> selectMyHistory(Page<SubmissionRow> page, @Param("userId") Long userId);

    /** 某用户在指定任务集合上的提交状态（任务列表页标记"未开始/草稿中/已完成"） */
    @Select("""
            <script>
            SELECT task_id AS taskId, status
            FROM task_submission
            WHERE user_id = #{userId}
              AND task_id IN
              <foreach item="id" collection="taskIds" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY updated_at DESC
            </script>
            """)
    List<UserTaskStatusRow> selectByUserAndTaskIds(@Param("userId") Long userId, @Param("taskIds") List<Long> taskIds);

    /** 用户完成过的任务数（去重）——首页"累计完成任务" */
    @Select("SELECT COUNT(DISTINCT task_id) FROM task_submission WHERE user_id = #{userId} AND status = 'EVALUATED'")
    long countDistinctCompletedTasks(@Param("userId") Long userId);

    /** 用户按类目的完成情况（能力分析页） */
    @Select("""
            SELECT t.category AS category, COUNT(DISTINCT s.task_id) AS completed
            FROM task_submission s
            JOIN task t ON t.id = s.task_id
            WHERE s.user_id = #{userId} AND s.status = 'EVALUATED'
            GROUP BY t.category
            """)
    List<CategoryCompletedRow> selectCompletedByCategory(@Param("userId") Long userId);

    record SubmissionRow(Long id, Long userId, Long taskId, String status, String content,
                         LocalDateTime createdAt, LocalDateTime updatedAt,
                         String taskTitle, String taskCategory, String taskDifficulty,
                         BigDecimal totalScore) {
    }

    record UserTaskStatusRow(Long taskId, String status) {
    }

    record CategoryCompletedRow(String category, Long completed) {
    }
}
