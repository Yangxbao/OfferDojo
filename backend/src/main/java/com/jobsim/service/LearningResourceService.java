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

import java.util.Map;
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

    /** 用户侧分页：多维筛选 + 后端排序（精选优先/Star/质量分/收录/推送/核验时间） */
    public PageResult<LearningResourceVO> page(String track, String sourceType, String licenseStatus,
                                               String verifiedStatus, String keyword,
                                               String qualityLevel, String language,
                                               Long minStars, Long maxStars, Boolean featuredOnly,
                                               String orderBy, Long page, Long size) {
        var wrapper = Wrappers.<LearningResource>lambdaQuery()
                .ne(LearningResource::getVerifiedStatus, LearningResource.STATUS_OFFLINE)
                .eq(StringUtils.hasText(track), LearningResource::getTrack, track)
                .eq(StringUtils.hasText(sourceType), LearningResource::getSourceType, sourceType)
                .eq(StringUtils.hasText(licenseStatus), LearningResource::getLicenseStatus, licenseStatus)
                .eq(StringUtils.hasText(verifiedStatus), LearningResource::getVerifiedStatus, verifiedStatus)
                .eq(StringUtils.hasText(qualityLevel), LearningResource::getQualityLevel, qualityLevel)
                .eq(StringUtils.hasText(language), LearningResource::getLanguage, language)
                .ge(minStars != null, LearningResource::getStarsCount, minStars == null ? 0 : minStars)
                .le(maxStars != null, LearningResource::getStarsCount, maxStars == null ? 0 : maxStars)
                .eq(Boolean.TRUE.equals(featuredOnly), LearningResource::getFeatured, true)
                .and(StringUtils.hasText(keyword),
                        w -> w.like(LearningResource::getTitle, keyword)
                                .or().like(LearningResource::getDescription, keyword)
                                .or().like(LearningResource::getCategoryTags, keyword));
        // 排序白名单（防注入）：featured 精选优先(默认) / stars / quality / recent 平台收录
        // / pushed GitHub 推送 / verified 最近核验
        switch (orderBy == null ? "featured" : orderBy) {
            case "stars" -> wrapper.orderByDesc(LearningResource::getStarsCount);
            case "quality" -> wrapper.orderByDesc(LearningResource::getQualityScore);
            case "recent" -> wrapper.orderByDesc(LearningResource::getCreatedAt);
            case "pushed" -> wrapper.orderByDesc(LearningResource::getGithubPushedAt);
            case "verified" -> wrapper.orderByDesc(LearningResource::getVerifiedAt);
            default -> wrapper.orderByDesc(LearningResource::getFeatured)
                    .orderByDesc(LearningResource::getQualityScore);
        }
        wrapper.orderByAsc(LearningResource::getId);
        IPage<LearningResource> result = resourceMapper.selectPage(PageUtils.of(page, size), wrapper);
        return PageResult.map(result, r -> toVO(r, null));
    }

    /** 质量等级统计（前端筛选栏徽标） */
    public Map<String, Long> qualityStats() {
        Map<String, Long> stats = new java.util.LinkedHashMap<>();
        for (var row : resourceMapper.selectList(Wrappers.<LearningResource>lambdaQuery()
                .select(LearningResource::getQualityLevel)
                .ne(LearningResource::getVerifiedStatus, LearningResource.STATUS_OFFLINE))) {
            String level = row.getQualityLevel() == null ? "PENDING_EVAL" : row.getQualityLevel();
            stats.merge(level, 1L, Long::sum);
        }
        return stats;
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
                r.getCategoryTags(), r.getLanguage(), r.getDifficultyLevel(),
                r.getStarsCount(), r.getForksCount(),
                r.getQualityScore(), r.getQualityLevel(),
                Boolean.TRUE.equals(r.getFeatured()), r.getReviewNote(),
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
