package com.jobsim.service.ai;

/**
 * 追问回答的反馈结果：分维度简化为「得分 + 点评 + 下一步建议」。
 */
public record FollowupFeedback(
        double score,
        String comment,
        String suggestion,
        String rawText
) {
}
