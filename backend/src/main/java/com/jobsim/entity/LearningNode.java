package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习路线节点：三条新路线（Java 全栈 / AI 全栈 / Python Agent）的有序学习节点，
 * 可关联资源（resource_codes）、实战任务（task_codes，引用任务题库编码，不复制题库）与前置节点。
 * 内容来自 classpath:learning/learning-nodes.json 增量播种。
 */
@Data
@TableName("learning_node")
public class LearningNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    /** java-fullstack / ai-fullstack / python-agent */
    private String track;

    /** 阶段序号，从 1 开始 */
    private Integer stageOrder;

    private Integer nodeOrder;

    private String title;

    private String goal;

    /** JSON 字符串数组：知识点 */
    private String knowledge;

    /** JSON 字符串数组：关联资源编码 */
    private String resourceCodes;

    /** JSON 字符串数组：关联实战任务编码 */
    private String taskCodes;

    /** JSON 字符串数组：前置节点编码 */
    private String prereqCodes;

    private String milestone;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
