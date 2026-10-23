package com.jobsim.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobsim.entity.Task;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TaskMapper extends BaseMapper<Task> {

    /** 已发布任务按类目统计（能力分析页的完成率用） */
    @Select("SELECT category, COUNT(*) AS total FROM task WHERE status = 'PUBLISHED' GROUP BY category")
    List<CategoryCountRow> selectPublishedCountByCategory();

    /** 批量统计任务的练习情况：尝试人数/次数/平均分（后台任务管理用） */
    @Select("""
            <script>
            SELECT e.task_id AS taskId,
                   COUNT(*) AS attempts,
                   COUNT(DISTINCT e.user_id) AS userCount,
                   ROUND(AVG(e.total_score), 1) AS avgScore
            FROM task_evaluation e
            <where>
                <if test="taskIds != null and taskIds.size() > 0">
                    e.task_id IN
                    <foreach item="id" collection="taskIds" open="(" separator="," close=")">#{id}</foreach>
                </if>
            </where>
            GROUP BY e.task_id
            </script>
            """)
    List<TaskStatsRow> selectTaskStats(@Param("taskIds") List<Long> taskIds);

    record CategoryCountRow(String category, Long total) {
    }

    record TaskStatsRow(Long taskId, Long attempts, Long userCount, Double avgScore) {
    }
}
