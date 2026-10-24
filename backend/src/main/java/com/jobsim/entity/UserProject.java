package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户项目展示：登录用户投稿自己的真实项目，审核通过后进入公共展示区。
 * Star/Fork 等仓库元数据由服务端经 GitHub API 获取，用户无法直接伪造。
 */
@Data
@TableName("user_project")
public class UserProject {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String subtitle;

    private String summary;

    private String problemSolved;

    /** 逗号分隔 */
    private String techStack;

    private String category;

    private String highlights;

    private String myRole;

    private String challenges;

    private String learnings;

    private String repoUrl;

    private String demoUrl;

    /** 封面图（受限上传或外链，渲染端防 XSS） */
    private String coverUrl;

    /** 项目说明 Markdown，服务端存储，前端渲染需 sanitize */
    private String readmeMd;

    private Boolean aiAssisted;

    private String aiUsageNote;

    /** 以下三项由服务端经 GitHubMetadataService 获取，用户不可直接写 */
    private Long ghStars;

    private Long ghForks;

    private String ghLanguage;

    private LocalDateTime ghFetchedAt;

    private String status;

    private String reviewComment;

    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
