package com.jobsim.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobsim.entity.Evaluation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /** 按天聚合评分趋势。用 CAST(... AS DATE)，MySQL 与 H2 都支持 */
    @Select("""
            SELECT CAST(created_at AS DATE) AS date,
                   ROUND(AVG(total_score), 1) AS avgScore,
                   COUNT(*) AS cnt
            FROM task_evaluation
            WHERE user_id = #{userId} AND created_at >= #{since}
            GROUP BY CAST(created_at AS DATE)
            ORDER BY date
            """)
    List<TrendRow> selectTrend(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /** 后台：评估记录列表（联用户名与任务标题） */
    @Select("""
            <script>
            SELECT e.id, e.submission_id AS submissionId, e.task_id AS taskId, e.user_id AS userId,
                   u.username, t.title AS taskTitle, e.total_score AS totalScore,
                   e.provider, e.notice, e.created_at AS createdAt
            FROM task_evaluation e
            JOIN sys_user u ON u.id = e.user_id
            JOIN task t ON t.id = e.task_id
            <where>
                <if test="taskId != null">e.task_id = #{taskId}</if>
            </where>
            ORDER BY e.created_at DESC
            </script>
            """)
    IPage<EvaluationRow> selectAdminPage(Page<EvaluationRow> page, @Param("taskId") Long taskId);

    /** 全站平均分（后台总览用） */
    @Select("SELECT ROUND(AVG(total_score), 1) FROM task_evaluation")
    Double selectAvgScore();

    record TrendRow(LocalDate date, BigDecimal avgScore, Integer cnt) {
    }

    record EvaluationRow(Long id, Long submissionId, Long taskId, Long userId, String username,
                         String taskTitle, BigDecimal totalScore, String provider, String notice,
                         LocalDateTime createdAt) {
    }
}
