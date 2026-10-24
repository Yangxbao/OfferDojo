package com.jobsim.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.common.BusinessException;
import com.jobsim.common.ErrorCode;
import com.jobsim.common.PageResult;
import com.jobsim.dto.learning.LearningResourceVO;
import com.jobsim.entity.LearningPlan;
import com.jobsim.entity.LearningResource;
import com.jobsim.mapper.LearningPlanMapper;
import com.jobsim.mapper.LearningResourceMapper;
import com.jobsim.util.JsonUtils;
import com.jobsim.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 学习资源服务：用户侧浏览（只展示非 OFFLINE 资源）与管理端维护。
 * 审核状态与使用权限分开处理：权限字段默认禁止，须管理员依据证据开放。
 */
@Service
@RequiredArgsConstructor
public class LearningResourceService {

    private final LearningResourceMapper resourceMapper;
    private final LearningPlanMapper planMapper;

    /** 用户侧分页：keyword 搜标题/描述；只返回非 OFFLINE */
    public PageResult<LearningResourceVO> page(String track, String sourceType, String licenseStatus,
                                               String verifiedStatus, String keyword, Long page, Long size) {
        var wrapper = Wrappers.<LearningResource>lambdaQuery()
                .ne(LearningResource::getVerifiedStatus, LearningResource.STATUS_OFFLINE)
                .eq(StringUtils.hasText(track), LearningResource::getTrack, track)
                .eq(StringUtils.hasText(sourceType), LearningResource::getSourceType, sourceType)
                .eq(StringUtils.hasText(licenseStatus), LearningResource::getLicenseStatus, licenseStatus)
                .eq(StringUtils.hasText(verifiedStatus), LearningResource::getVerifiedStatus, verifiedStatus)
                .and(StringUtils.hasText(keyword),
                        w -> w.like(LearningResource::getTitle, keyword)
                                .or().like(LearningResource::getDescription, keyword))
                .orderByAsc(LearningResource::getTrack)
                .orderByAsc(LearningResource::getCode);
        IPage<LearningResource> result = resourceMapper.selectPage(PageUtils.of(page, size), wrapper);
        return PageResult.map(result, r -> toVO(r, null));
    }

    public LearningResourceVO detail(Long userId, Long id) {
        LearningResource resource = resourceMapper.selectById(id);
        if (resource == null || LearningResource.STATUS_OFFLINE.equals(resource.getVerifiedStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在或已下架");
        }
        boolean inPlan = userId != null && planMapper.selectCount(Wrappers.<LearningPlan>lambdaQuery()
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getItemType, "RESOURCE")
                .eq(LearningPlan::getItemCode, resource.getCode())) > 0;
        return toVO(resource, inPlan);
    }

    /** 用户加入计划的资源编码集合（路线页标记"已在计划中"用） */
    public Set<String> myPlanResourceCodes(Long userId) {
        return planMapper.selectList(Wrappers.<LearningPlan>lambdaQuery()
                        .select(LearningPlan::getItemCode)
                        .eq(LearningPlan::getUserId, userId)
                        .eq(LearningPlan::getItemType, "RESOURCE"))
                .stream().map(LearningPlan::getItemCode).collect(java.util.stream.Collectors.toSet());
    }

    public LearningResourceVO toVO(LearningResource r, Boolean inMyPlan) {
        return new LearningResourceVO(
                r.getId(), r.getCode(), r.getTrack(), trackLabel(r.getTrack()),
                r.getTitle(), r.getDescription(), r.getUrl(), r.getSourceType(),
                r.getAudience(),
                JsonUtils.readList(r.getPrerequisite(), String.class),
                r.getVersionNote(),
                r.getLicenseSpdx(), r.getLicenseName(), r.getLicensePath(), r.getLicenseStatus(),
                r.getVerifiedStatus(), r.getVerifiedAt(), r.getVerificationEvidence(),
                Boolean.TRUE.equals(r.getAllowCopy()),
                Boolean.TRUE.equals(r.getAllowRedistribute()),
                Boolean.TRUE.equals(r.getAttributionRequired()),
                Boolean.TRUE.equals(r.getShareAlike()),
                r.getRightsBasis(), r.getAdminNote(),
                Boolean.TRUE.equals(inMyPlan));
    }

    public static String trackLabel(String track) {
        return switch (track) {
            case "java-fullstack" -> "Java 全栈开发";
            case "ai-fullstack" -> "AI 全栈应用开发";
            case "python-agent" -> "Python Agent 开发";
            default -> track;
        };
    }
}
