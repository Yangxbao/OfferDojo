package com.jobsim.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用日志：后台管理查看评估失败记录用。
 * message 只记录错误类型与简述，绝不包含 API Key 等敏感信息。
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** REMOTE / MOCK */
    private String provider;

    private String model;

    private String taskCode;

    private Boolean success;

    /** TIMEOUT / HTTP_ERROR / PARSE_ERROR / UNKNOWN */
    private String errorType;

    private String message;

    private Integer durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
