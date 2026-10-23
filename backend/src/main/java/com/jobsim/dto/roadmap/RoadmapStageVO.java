package com.jobsim.dto.roadmap;

import java.util.List;

/**
 * 新手学习路线图：按阶段组织，每个阶段给出目标、先学知识、推荐任务与里程碑。
 * 任务列表由后端按编码解析为真实任务（含当前用户的练习状态），前端直接渲染时间线。
 */
public record RoadmapStageVO(
        int order,
        String title,
        String goal,
        List<String> knowledge,
        List<TaskBrief> tasks,
        String milestone
) {

    /** 路线图中引用的任务摘要（带当前用户进度） */
    public record TaskBrief(
            Long id,
            String code,
            String title,
            String difficulty,
            Integer estimatedMinutes,
            String myStatus,
            Double myBestScore
    ) {
    }
}
