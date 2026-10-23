package com.jobsim.dto.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 评分标准条目。全系统使用固定的六个能力维度，任务只选择其中的子集并分配权重。
 */
public record RubricItem(
        @NotBlank(message = "评分维度不能为空")
        String dimension,

        @NotNull(message = "维度权重不能为空")
        @Min(value = 1, message = "维度权重至少为 1")
        @Max(value = 100, message = "维度权重最多为 100")
        Integer weight,

        @NotBlank(message = "评分指引不能为空")
        String guidance
) {
}
