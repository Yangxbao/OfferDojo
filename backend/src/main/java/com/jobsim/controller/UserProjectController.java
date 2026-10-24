package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.common.PageResult;
import com.jobsim.entity.UserProject;
import com.jobsim.security.LoginUser;
import com.jobsim.service.GitHubSearchService;
import com.jobsim.service.UserProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "项目展示", description = "用户项目投稿/我的项目/公共展示区")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserProjectController {

    private final UserProjectService projectService;
    private final GitHubSearchService gitHubSearchService;

    // ==================== 用户侧 ====================

    @Operation(summary = "我的项目列表（含各状态）")
    @GetMapping("/my/projects")
    public ApiResponse<PageResult<UserProject>> myProjects(@AuthenticationPrincipal LoginUser user,
                                                           @RequestParam(required = false) Long page,
                                                           @RequestParam(required = false) Long size) {
        return ApiResponse.ok(projectService.myProjects(user.id(), page, size));
    }

    @Operation(summary = "新建项目草稿")
    @PostMapping("/my/projects")
    public ApiResponse<UserProject> create(@AuthenticationPrincipal LoginUser user,
                                           @RequestBody UserProject input) {
        return ApiResponse.ok(projectService.create(user.id(), input));
    }

    @Operation(summary = "编辑我的项目（仅草稿/被驳回/已撤回）")
    @PutMapping("/my/projects/{id}")
    public ApiResponse<UserProject> update(@AuthenticationPrincipal LoginUser user,
                                          @PathVariable Long id,
                                          @RequestBody UserProject input) {
        return ApiResponse.ok(projectService.update(user.id(), id, input));
    }

    @Operation(summary = "提交审核（自动尝试回填 GitHub 元数据）")
    @PutMapping("/my/projects/{id}/submit")
    public ApiResponse<UserProject> submit(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return ApiResponse.ok(projectService.submit(user.id(), id));
    }

    @Operation(summary = "撤回")
    @PutMapping("/my/projects/{id}/withdraw")
    public ApiResponse<UserProject> withdraw(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return ApiResponse.ok(projectService.withdraw(user.id(), id));
    }

    @Operation(summary = "删除（未发布）")
    @DeleteMapping("/my/projects/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        projectService.delete(user.id(), id);
        return ApiResponse.ok();
    }

    // ==================== 公共展示 ====================

    @Operation(summary = "公共项目展示区（仅审核通过；orderBy: recent/stars）")
    @GetMapping("/projects")
    public ApiResponse<PageResult<UserProject>> publicPage(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "recent") String orderBy,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(projectService.publicPage(category, keyword, orderBy, page, size));
    }

    @Operation(summary = "项目公开详情")
    @GetMapping("/projects/{id}")
    public ApiResponse<UserProject> publicDetail(@PathVariable Long id) {
        return ApiResponse.ok(projectService.publicDetail(id));
    }

    @Operation(summary = "项目分类选项（前后端统一编码）")
    @GetMapping("/projects/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(UserProjectService.CATEGORIES);
    }

    // ==================== 管理端 ====================

    @Operation(summary = "管理端：项目投稿列表（可按状态过滤）")
    @GetMapping("/admin/projects")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<UserProject>> adminPage(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Long page,
                                                          @RequestParam(required = false) Long size) {
        return ApiResponse.ok(projectService.adminPage(status, page, size));
    }

    public record ReviewRequest(boolean approve, String comment) {
    }

    @Operation(summary = "管理端：审核项目投稿")
    @PutMapping("/admin/projects/{id}/review")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserProject> review(@PathVariable Long id, @RequestBody ReviewRequest request) {
        return ApiResponse.ok(projectService.review(id, request.approve(), request.comment()));
    }

    @Operation(summary = "管理端：下架已发布项目")
    @PutMapping("/admin/projects/{id}/offline")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserProject> offline(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ApiResponse.ok(projectService.offline(id, reason));
    }

    // ==================== GitHub 榜单 ====================

    @Operation(summary = "GitHub 热门项目榜（真实 API；失败返回缓存提示）")
    @GetMapping("/github/top")
    public ApiResponse<Map<String, Object>> githubTop(
            @RequestParam(defaultValue = "java-backend") String category,
            @RequestParam(defaultValue = "15") int limit) {
        return ApiResponse.ok(gitHubSearchService.searchTop(category, limit));
    }

    @Operation(summary = "榜单分类选项（与检索条件映射）")
    @GetMapping("/github/top/categories")
    public ApiResponse<List<Map<String, String>>> githubCategories() {
        List<Map<String, String>> result = new java.util.ArrayList<>();
        GitHubSearchService.TOPIC_QUERIES.forEach((key, query) -> {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put("key", key);
            m.put("label", labelOf(key));
            result.add(m);
        });
        return ApiResponse.ok(result);
    }

    private String labelOf(String key) {
        return switch (key) {
            case "java-backend" -> "Java 后端";
            case "frontend" -> "前端";
            case "python" -> "Python";
            case "ai-agent" -> "AI / Agent";
            case "rag" -> "RAG";
            case "database" -> "数据库";
            case "devops" -> "DevOps";
            case "testing" -> "测试";
            default -> key;
        };
    }
}
