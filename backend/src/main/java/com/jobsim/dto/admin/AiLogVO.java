package com.jobsim.dto.admin;

import java.time.LocalDateTime;

/** AI 调用日志（含失败记录） */
public record AiLogVO(
        Long id,
        String provider,
        String model,
        String taskCode,
        boolean success,
        String errorType,
        String message,
        Integer durationMs,
        LocalDateTime createdAt
) {
}
