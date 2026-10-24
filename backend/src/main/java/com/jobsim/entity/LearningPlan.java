package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人学习计划与记录：用户对资源/路线节点的学习状态。
 * 与登录用户强关联（user_id），查询一律按当前用户过滤，不暴露他人记录。
 */
@Data
@TableName("learning_plan")
public class LearningPlan {

    public static final String STATUS_TODO = "TODO";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_DONE = "DONE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** RESOURCE / NODE */
    private String itemType;

    /** 资源编码或节点编码 */
    private String itemCode;

    /** TODO / IN_PROGRESS / DONE */
    private String status;

    private String note;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
