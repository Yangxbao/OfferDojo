package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 评估结果：与提交一一对应（submission_id 唯一）。
 * 冗余 user_id / task_id 是为了让"按人/按任务统计"不用多表 JOIN，
 * 属于读多写少场景下有意的反范式设计。
 */
@Data
@TableName("task_evaluation")
public class Evaluation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submissionId;

    private Long userId;

    private Long taskId;

    /** 加权总分 0.0 - 10.0 */
    private BigDecimal totalScore;

    /** JSON：[{dimension, score, weight, comment}] */
    private String dimensionScores;

    /** JSON 字符串数组：正确做法 */
    private String strengths;

    /** JSON 字符串数组：存在问题 */
    private String problems;

    /** JSON 字符串数组：可能遗漏的边界情况 */
    private String edgeCases;

    /** JSON 字符串数组：改进建议 */
    private String suggestions;

    /** JSON 字符串数组：技术追问 */
    private String followUps;

    /** JSON 字符串数组：建议下一步练习的任务编码 */
    private String nextTaskCodes;

    /** MOCK / REMOTE / MOCK_FALLBACK（远程失败后降级） */
    private String provider;

    /** 远程模型名；本地模拟为空 */
    private String model;

    /** 提示信息（如降级说明、免责提醒） */
    private String notice;

    /** 评估提示词版本，用于追溯与迭代对比 */
    private String promptVersion;

    /** 原始返回：Remote 为模型原文（可追溯），Mock 为评分明细 JSON */
    private String rawResponse;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
