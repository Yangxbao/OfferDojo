package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社区面试题投稿：用户提交八股/面试复盘/实战题，审核通过后进入社区题库。
 * 授权字段记录用户的许可选择——未明确授权公开展示的内容仅限审核，不得对外展示。
 */
@Data
@TableName("bagu_submission")
public class BaguSubmission {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    /** 授权类型：仅提交审核 / 授权公开展示 */
    public static final String GRANT_REVIEW_ONLY = "REVIEW_ONLY";
    public static final String GRANT_PUBLIC_DISPLAY = "PUBLIC_DISPLAY";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 与官方题库同一套类目编码 */
    private String category;

    /** BAGU / INTERVIEW / PROJECT / SYSTEM_DESIGN / DEBUG */
    private String questionType;

    private String difficulty;

    private String title;

    /** 题目内容（Markdown） */
    private String content;

    private String myAnswer;

    private String referenceAnswer;

    private String example;

    private String tags;

    /** 公司/岗位信息（自愿 + 建议匿名化） */
    private String companyInfo;

    private String referenceUrl;

    /** 是否匿名展示作者 */
    private Boolean anonymous;

    /** USER_ORIGINAL 用户原创 / USER_REVIEWED 用户复盘整理 */
    private String originType;

    /** 授权：REVIEW_ONLY / PUBLIC_DISPLAY */
    private String licenseGrant;

    private LocalDateTime licenseGrantedAt;

    private String status;

    private String reviewComment;

    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
