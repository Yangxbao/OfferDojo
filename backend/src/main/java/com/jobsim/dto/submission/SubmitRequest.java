package com.jobsim.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 提交任务请求 */
public record SubmitRequest(
        @NotBlank(message = "答案内容不能为空")
        @Size(max = 20000, message = "答案内容最长 20000 字")
        String content
) {
}
