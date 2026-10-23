package com.jobsim.service.ai;

import com.jobsim.dto.task.RubricItem;

import java.util.List;

/**
 * 评估上下文：Provider 需要的全部输入。
 * Provider 只依赖这个 record，不接触数据库实体，方便单独做单元测试。
 */
public record EvaluationContext(
        String taskCode,
        String title,
        String background,
        List<String> requirements,
        List<String> constraints,
        List<String> deliverables,
        List<String> acceptance,
        List<RubricItem> rubric,
        String answer,
        /** 系统中所有已发布任务编码，用于校验"下一步推荐任务"的合法性 */
        List<String> allTaskCodes,
        /** 模拟评估的提示数据（关键词/参考做法等）；管理员手工创建的任务可能为 null */
        MockHints mockHints
) {
}
