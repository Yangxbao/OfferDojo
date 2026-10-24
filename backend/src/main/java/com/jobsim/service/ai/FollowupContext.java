package com.jobsim.service.ai;

import java.util.List;

/**
 * 追问模拟面试的输入上下文：把评估报告里的追问变成可作答的多轮对话。
 * Provider 只依赖这个 record，不接触数据库实体。
 */
public record FollowupContext(
        String taskTitle,
        String question,
        String userAnswer,
        /** 原任务答案摘要（供 AI 结合上下文评估，避免答非所问） */
        String originalAnswerExcerpt,
        /** 历史对话（问/答/反馈），多轮追问时提供上下文 */
        List<HistoryItem> history
) {

    public record HistoryItem(String question, String answer, String feedback) {
    }
}
