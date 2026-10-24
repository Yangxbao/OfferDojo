package com.jobsim.dto.learning;

import java.util.List;

/** 学习路线 VO：按阶段分组的节点（带当前用户进度与关联任务的真实验收状态） */
public record LearningTrackVO(
        String track,
        String trackLabel,
        String description,
        List<StageVO> stages
) {

    public record StageVO(int stageOrder, String stageTitle, List<NodeVO> nodes) {
    }

    public record NodeVO(
            Long id,
            String code,
            int nodeOrder,
            String title,
            String goal,
            List<String> knowledge,
            List<ResourceBrief> resources,
            List<TaskBrief> tasks,
            List<String> prereqCodes,
            String milestone,
            /** 当前用户对该节点的计划状态：null 表示未加入计划 */
            String planStatus,
            String planNote
    ) {
    }

    public record ResourceBrief(String code, String title, String url, String licenseSpdx,
                                String verifiedStatus, String sourceType) {
    }

    /** 关联实战任务的真实验收状态（来自任务题库与评估记录，不由用户点击声明） */
    public record TaskBrief(Long id, String code, String title, String difficulty,
                            String myStatus, Double myBestScore) {
    }
}
