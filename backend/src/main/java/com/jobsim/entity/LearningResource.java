package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开源学习资源元数据。
 * 只存元数据与来源链接，不复制外部内容本体；
 * 审核状态与内容使用权限分开建模（见字段注释）。
 * 内容来自 classpath:learning/learning-resources.json 增量播种，管理员修改保存在数据库。
 */
@Data
@TableName("learning_resource")
public class LearningResource {

    public static final String STATUS_PENDING = "PENDING";     // 待核验
    public static final String STATUS_VERIFIED = "VERIFIED";   // 已核验（元数据与来源确认）
    public static final String STATUS_PUBLISHED = "PUBLISHED"; // 可展示（内容审核通过）
    public static final String STATUS_OFFLINE = "OFFLINE";     // 已下架

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    /** java-fullstack / ai-fullstack / python-agent */
    private String track;

    private String title;

    private String description;

    private String url;

    /** ROADMAP / NOTES / EXAMPLES / DOCS / COURSE */
    private String sourceType;

    private String audience;

    /** JSON 字符串数组：前置知识 */
    private String prerequisite;

    private String versionNote;

    private String licenseSpdx;

    private String licenseName;

    private String licensePath;

    /** UNKNOWN / VERIFIED / NOASSERTION */
    private String licenseStatus;

    /** PENDING / VERIFIED / PUBLISHED / OFFLINE */
    private String verifiedStatus;

    private LocalDateTime verifiedAt;

    /** 核验证据 JSON：默认分支/最近提交/核验项/未核实字段 */
    private String verificationEvidence;

    /** 内容使用权限：未核验时一律默认禁止 */
    private Boolean allowCopy;

    private Boolean allowRedistribute;

    private Boolean attributionRequired;

    private Boolean shareAlike;

    /** 权限判定依据与证据链接 */
    private String rightsBasis;

    private String adminNote;

    /** 多标签（逗号分隔），与主分类 track 并存 */
    private String categoryTags;

    /** 主要编程语言 */
    private String language;

    /** BEGINNER / INTERMEDIATE / ADVANCED */
    private String difficultyLevel;

    /** 最近一次真实获取的 Star 数（来自 GitHub API，非手工填写） */
    private Long starsCount;

    private Long forksCount;

    private LocalDateTime githubCreatedAt;

    private LocalDateTime githubUpdatedAt;

    private LocalDateTime githubPushedAt;

    /** 编辑审核质量分 0-100（有依据的评分，非 Star 自动推算） */
    private Integer qualityScore;

    /** FEATURED 精选 / RECOMMENDED 推荐 / NICHE 小众 */
    private String qualityLevel;

    /** 是否进入精选（低质量/失效/许可不清不得为 1） */
    private Boolean featured;

    /** 质量判断依据及问题说明 */
    private String reviewNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
