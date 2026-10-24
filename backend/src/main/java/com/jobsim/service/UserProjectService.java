package com.jobsim.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.common.BusinessException;
import com.jobsim.common.ErrorCode;
import com.jobsim.common.PageResult;
import com.jobsim.entity.UserProject;
import com.jobsim.mapper.UserProjectMapper;
import com.jobsim.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 用户项目服务：草稿→提交审核→通过/驳回→撤回 的状态机 + 归属校验。
 * GitHub 元数据（Star/Fork/语言）仅由服务端经 GitHubMetadataService 回填，用户输入不落这些字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProjectService {

    private final UserProjectMapper projectMapper;
    private final GitHubMetadataService gitHubMetadataService;

    /** 允许的项目分类（与资源中心通用分类共用一套编码思想，前端下拉一致） */
    public static final List<String> CATEGORIES = List.of(
            "java-backend", "frontend", "python", "ai-agent", "rag",
            "database", "distributed", "devops", "testing", "open-source");

    // ==================== 用户侧 ====================

    public PageResult<UserProject> myProjects(Long userId, Long page, Long size) {
        IPage<UserProject> result = projectMapper.selectPage(PageUtils.of(page, size),
                Wrappers.<UserProject>lambdaQuery()
                        .eq(UserProject::getUserId, userId)
                        .orderByDesc(UserProject::getUpdatedAt));
        return PageResult.from(result);
    }

    public UserProject create(Long userId, UserProject input) {
        validate(input);
        UserProject project = new UserProject();
        applyInput(project, input);
        project.setUserId(userId);
        project.setStatus(UserProject.STATUS_DRAFT);
        projectMapper.insert(project);
        fetchGithubMeta(project, false);
        return project;
    }

    public UserProject update(Long userId, Long id, UserProject input) {
        UserProject project = requireOwned(userId, id);
        // 只有 DRAFT / REJECTED / WITHDRAWN 可编辑（已提交/已通过不可改）
        if (UserProject.STATUS_PENDING.equals(project.getStatus())
                || UserProject.STATUS_APPROVED.equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可编辑（已提交审核或已发布）");
        }
        validate(input);
        applyInput(project, input);
        projectMapper.updateById(project);
        return project;
    }

    /** 提交审核：DRAFT/REJECTED/WITHDRAWN → PENDING_REVIEW，并尝试回填仓库元数据 */
    public UserProject submit(Long userId, Long id) {
        UserProject project = requireOwned(userId, id);
        if (!UserProject.STATUS_DRAFT.equals(project.getStatus())
                && !UserProject.STATUS_REJECTED.equals(project.getStatus())
                && !UserProject.STATUS_WITHDRAWN.equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅草稿/被驳回/已撤回状态可提交审核");
        }
        project.setStatus(UserProject.STATUS_PENDING);
        project.setReviewComment(null);
        projectMapper.updateById(project);
        fetchGithubMeta(project, true);
        return project;
    }

    public UserProject withdraw(Long userId, Long id) {
        UserProject project = requireOwned(userId, id);
        if (UserProject.STATUS_APPROVED.equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布项目请联系管理员下架");
        }
        project.setStatus(UserProject.STATUS_WITHDRAWN);
        projectMapper.updateById(project);
        return project;
    }

    public void delete(Long userId, Long id) {
        UserProject project = requireOwned(userId, id);
        if (UserProject.STATUS_APPROVED.equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布项目不能删除，需先由管理员下架");
        }
        projectMapper.deleteById(project.getId());
    }

    // ==================== 公共展示 ====================

    /** 公共展示区：仅 APPROVED，支持分类/技术栈关键词筛选与排序 */
    public PageResult<UserProject> publicPage(String category, String keyword,
                                              String orderBy, Long page, Long size) {
        var wrapper = Wrappers.<UserProject>lambdaQuery()
                .eq(UserProject::getStatus, UserProject.STATUS_APPROVED)
                .eq(StringUtils.hasText(category), UserProject::getCategory, category)
                .and(StringUtils.hasText(keyword),
                        w -> w.like(UserProject::getName, keyword)
                                .or().like(UserProject::getTechStack, keyword)
                                .or().like(UserProject::getSummary, keyword));
        // 排序口径（白名单防注入）：recent 最新收录 / stars Star 数
        switch (orderBy == null ? "recent" : orderBy) {
            case "stars" -> wrapper.orderByDesc(UserProject::getGhStars).orderByDesc(UserProject::getId);
            default -> wrapper.orderByDesc(UserProject::getUpdatedAt);
        }
        return PageResult.from(projectMapper.selectPage(PageUtils.of(page, size), wrapper));
    }

    public UserProject publicDetail(Long id) {
        UserProject project = projectMapper.selectById(id);
        if (project == null || !UserProject.STATUS_APPROVED.equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在或未公开");
        }
        return project;
    }

    // ==================== 管理端 ====================

    public PageResult<UserProject> adminPage(String status, Long page, Long size) {
        return PageResult.from(projectMapper.selectPage(PageUtils.of(page, size),
                Wrappers.<UserProject>lambdaQuery()
                        .eq(StringUtils.hasText(status), UserProject::getStatus, status)
                        .orderByAsc(UserProject::getStatus)
                        .orderByDesc(UserProject::getUpdatedAt)));
    }

    /** 审核：通过 → APPROVED；驳回 → REJECTED + 意见 */
    public UserProject review(Long id, boolean approve, String comment) {
        UserProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        if (!UserProject.STATUS_PENDING.equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审核状态可审核");
        }
        project.setStatus(approve ? UserProject.STATUS_APPROVED : UserProject.STATUS_REJECTED);
        project.setReviewComment(comment);
        project.setReviewedAt(java.time.LocalDateTime.now());
        projectMapper.updateById(project);
        return project;
    }

    /** 管理员下架已发布项目 */
    public UserProject offline(Long id, String reason) {
        UserProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        project.setStatus(UserProject.STATUS_WITHDRAWN);
        project.setReviewComment("管理员下架：" + (reason == null ? "" : reason));
        projectMapper.updateById(project);
        return project;
    }

    // ==================== 私有 ====================

    private UserProject requireOwned(Long userId, Long id) {
        UserProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己的项目");
        }
        return project;
    }

    /** 服务端回填 GitHub 元数据：失败不影响提交（用户介绍仍可保存），标记数据不可用 */
    private void fetchGithubMeta(UserProject project, boolean forceRefresh) {
        if (project.getRepoUrl() == null || project.getRepoUrl().isBlank()
                || !project.getRepoUrl().contains("github.com")) {
            return; // Gitee 等来源本版不获取实时元数据，保留链接
        }
        if (!forceRefresh && project.getGhFetchedAt() != null) {
            return;
        }
        Map<String, Object> meta = gitHubMetadataService.verify(project.getRepoUrl());
        if (Boolean.TRUE.equals(meta.get("success"))) {
            project.setGhStars(meta.get("stars") instanceof Number n ? n.longValue() : null);
            project.setGhForks(null); // search API 的 forks 单独取；repo API 无 forks 时留空，不编造
            project.setGhLanguage(null);
            project.setGhFetchedAt(java.time.LocalDateTime.now());
        } else {
            project.setGhFetchedAt(null);
            log.info("项目 {} 仓库元数据获取失败：{}", project.getId(), meta.get("message"));
        }
        projectMapper.updateById(project);
    }

    private void validate(UserProject input) {
        if (!StringUtils.hasText(input.getName()) || input.getName().length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目名称必填且不超过 100 字");
        }
        if (!StringUtils.hasText(input.getSummary())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目简介必填");
        }
        if (!StringUtils.hasText(input.getTechStack())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "技术栈必填");
        }
        if (!StringUtils.hasText(input.getCategory()) || !CATEGORIES.contains(input.getCategory())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目分类不合法");
        }
        if (input.getReadmeMd() != null && input.getReadmeMd().length() > 50000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目说明最长 50000 字");
        }
        // URL 白名单校验：只允许 http(s) 外链，防止 javascript: 等注入
        validateUrl(input.getRepoUrl());
        validateUrl(input.getDemoUrl());
        validateUrl(input.getCoverUrl());
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "链接必须以 http(s):// 开头");
        }
        if (url.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "链接过长");
        }
    }

    private void applyInput(UserProject project, UserProject input) {
        project.setName(input.getName());
        project.setSubtitle(input.getSubtitle());
        project.setSummary(input.getSummary());
        project.setProblemSolved(input.getProblemSolved());
        project.setTechStack(input.getTechStack());
        project.setCategory(input.getCategory());
        project.setHighlights(input.getHighlights());
        project.setMyRole(input.getMyRole());
        project.setChallenges(input.getChallenges());
        project.setLearnings(input.getLearnings());
        project.setRepoUrl(input.getRepoUrl());
        project.setDemoUrl(input.getDemoUrl());
        project.setCoverUrl(input.getCoverUrl());
        project.setReadmeMd(input.getReadmeMd());
        project.setAiAssisted(Boolean.TRUE.equals(input.getAiAssisted()));
        project.setAiUsageNote(input.getAiUsageNote());
        // 注意：ghStars/ghForks/ghLanguage 不从用户输入赋值——只能由服务端回填
    }
}
