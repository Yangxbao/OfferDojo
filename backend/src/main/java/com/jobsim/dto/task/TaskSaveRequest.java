package com.jobsim.dto.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * 管理端新建/编辑任务请求。
 * 列表字段使用容器元素校验（List<@NotBlank String>），保证每行内容非空。
 */
@Data
public class TaskSaveRequest {

    @NotBlank(message = "任务编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9-]{2,32}$", message = "任务编码只能包含字母、数字和中划线")
    private String code;

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200, message = "任务标题最长 200 字")
    private String title;

    @NotBlank(message = "类目不能为空")
    private String category;

    @NotBlank(message = "难度不能为空")
    @Pattern(regexp = "^(EASY|MEDIUM|HARD)$", message = "难度只能为 EASY / MEDIUM / HARD")
    private String difficulty;

    private List<String> tags;

    @NotBlank(message = "任务简介不能为空")
    @Size(max = 500, message = "任务简介最长 500 字")
    private String summary;

    @NotNull(message = "预计用时不能为空")
    @Min(value = 5, message = "预计用时至少 5 分钟")
    @Max(value = 480, message = "预计用时最多 480 分钟")
    private Integer estimatedMinutes;

    @NotBlank(message = "任务背景不能为空")
    private String background;

    @NotEmpty(message = "业务需求至少一条")
    private List<@NotBlank @Size(max = 1000, message = "单条内容最长 1000 字") String> requirements;

    @NotEmpty(message = "约束条件至少一条")
    private List<@NotBlank @Size(max = 1000, message = "单条内容最长 1000 字") String> constraintItems;

    @NotEmpty(message = "预期交付物至少一条")
    private List<@NotBlank @Size(max = 1000, message = "单条内容最长 1000 字") String> deliverables;

    @NotEmpty(message = "验收标准至少一条")
    private List<@NotBlank @Size(max = 1000, message = "单条内容最长 1000 字") String> acceptance;

    @NotEmpty(message = "评分标准至少一个维度")
    @Valid
    private List<RubricItem> rubric;

    @Pattern(regexp = "^(DRAFT|PUBLISHED|OFFLINE)$", message = "状态不合法")
    private String status;

    @Min(0)
    private Integer sortOrder;
}
