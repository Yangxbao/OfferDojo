package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟任务实体。
 * 列表类内容（需求、约束、评分标准等）以 JSON 字符串存库：
 * 结构会随产品迭代变化，且这些内容总是整块读写，拆成多表反而增加复杂度。
 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编码（业务主键），如 API-001 */
    private String code;

    private String title;

    /** 类目编码：api-design / db-design / troubleshooting / testing / refactoring */
    private String category;

    /** EASY / MEDIUM / HARD */
    private String difficulty;

    /** 逗号分隔标签 */
    private String tags;

    private String summary;

    private Integer estimatedMinutes;

    private String background;

    /** JSON 字符串数组 */
    private String requirements;

    /** JSON 字符串数组（列名避开保留字 constraints，用 constraint_items） */
    private String constraintItems;

    /** JSON 字符串数组 */
    private String deliverables;

    /** JSON 字符串数组 */
    private String acceptance;

    /** JSON：[{dimension, weight, guidance}] */
    private String rubricJson;

    /** JSON：模拟评估提示（关键词/参考做法等），管理员手工创建的任务可为空 */
    private String mockHintsJson;

    /** DRAFT / PUBLISHED / OFFLINE */
    private String status;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
