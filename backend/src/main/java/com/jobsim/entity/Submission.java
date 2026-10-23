package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务提交。DRAFT = 草稿（可反复保存），EVALUATED = 已提交并完成评估。
 * 每次重新提交都产生新记录，天然保留练习历史。
 */
@Data
@TableName("task_submission")
public class Submission {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_EVALUATED = "EVALUATED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long taskId;

    private String content;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
