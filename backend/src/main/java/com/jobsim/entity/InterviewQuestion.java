package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 八股复习题目：内容来自 classpath:bagu/bagu-*.json，按编码增量播种。
 * 一条记录就是一道高频面试知识题：一句话总结（背诵）+ 大白话解释 + 要点 + 示例 + 追问。
 */
@Data
@TableName("interview_question")
public class InterviewQuestion {

    public static final String FREQ_HIGH = "HIGH";
    public static final String FREQ_MID = "MID";
    public static final String FREQ_LOW = "LOW";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    /** 类目编码（与 task.category 同一套） */
    private String category;

    private String title;

    /** HIGH / MID / LOW */
    private String frequency;

    /** EASY / MEDIUM / HARD / EXPERT */
    private String difficulty;

    private String tags;

    /** 一句话总结（快速背诵） */
    private String oneLiner;

    /** 大白话解释 */
    private String plain;

    /** JSON 字符串数组：背诵要点 */
    private String points;

    /** 示例（代码或场景） */
    private String example;

    /** JSON 字符串数组：常见追问 */
    private String followUps;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
