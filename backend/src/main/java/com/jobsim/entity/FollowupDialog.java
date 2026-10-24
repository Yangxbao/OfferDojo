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
 * 追问模拟面试对话：评估报告中的"技术追问"从只读变成可作答，
 * 每次回答由 AI（或 Mock）按问题与原始答案上下文给出反馈，沉淀为对话记录。
 */
@Data
@TableName("followup_dialog")
public class FollowupDialog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long evaluationId;

    private Long userId;

    private String question;

    private String answer;

    /** 回答得分 0.0-10.0 */
    private BigDecimal score;

    private String feedback;

    /** MOCK / REMOTE / MOCK_FALLBACK */
    private String provider;

    private String notice;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
