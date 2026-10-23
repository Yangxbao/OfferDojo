package com.jobsim.dto.bagu;

import java.util.List;

/**
 * 八股复习题目 VO。
 * 展示结构：标题 + 高频标签 → 一句话总结（先背这个）→ 大白话解释 → 背诵要点 → 示例 → 追问。
 */
public record BaguVO(
        Long id,
        String code,
        String category,
        String categoryLabel,
        String title,
        String frequency,
        String difficulty,
        List<String> tags,
        String oneLiner,
        String plain,
        List<String> points,
        String example,
        List<String> followUps
) {
}
