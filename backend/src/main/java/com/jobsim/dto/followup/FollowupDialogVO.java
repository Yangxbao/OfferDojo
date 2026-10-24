package com.jobsim.dto.followup;

import java.time.LocalDateTime;

/** 追问对话记录 VO */
public record FollowupDialogVO(
        Long id,
        Long evaluationId,
        String question,
        String answer,
        double score,
        String feedback,
        String provider,
        String notice,
        LocalDateTime createdAt
) {
}
