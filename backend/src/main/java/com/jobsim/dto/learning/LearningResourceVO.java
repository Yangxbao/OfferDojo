package com.jobsim.dto.learning;

import java.time.LocalDateTime;
import java.util.List;

/** 学习资源 VO：详情含核验证据与权限信息 */
public record LearningResourceVO(
        Long id,
        String code,
        String track,
        String trackLabel,
        String title,
        String description,
        String url,
        String sourceType,
        String audience,
        List<String> prerequisite,
        String versionNote,
        String licenseSpdx,
        String licenseName,
        String licensePath,
        String licenseStatus,
        String verifiedStatus,
        LocalDateTime verifiedAt,
        String verificationEvidence,
        boolean allowCopy,
        boolean allowRedistribute,
        boolean attributionRequired,
        boolean shareAlike,
        String rightsBasis,
        String adminNote,
        /** 当前用户是否已加入学习计划（未登录场景恒为 false） */
        boolean inMyPlan
) {
}
