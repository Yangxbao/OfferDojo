package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.common.PageResult;
import com.jobsim.entity.BaguSubmission;
import com.jobsim.security.LoginUser;
import com.jobsim.service.BaguSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

@Tag(name = "社区题库", description = "面试题投稿 / 我的投稿 / 公共社区题库（审核通过且授权公开展示）")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BaguSubmissionController {

    private final BaguSubmissionService submissionService;

    // ==================== 用户侧 ====================

    @Operation(summary = "我的投稿列表")
    @GetMapping("/my/bagu-submissions")
    public ApiResponse<PageResult<Map<String, Object>>> mySubmissions(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(submissionService.mySubmissions(user.id(), page, size));
    }

    @Operation(summary = "新建投稿草稿")
    @PostMapping("/my/bagu-submissions")
    public ApiResponse<BaguSubmission> create(@AuthenticationPrincipal LoginUser user,
                                              @RequestBody BaguSubmission input) {
        return ApiResponse.ok(submissionService.create(user.id(), input));
    }

    @Operation(summary = "编辑投稿（草稿/被驳回/已撤回）")
    @PutMapping("/my/bagu-submissions/{id}")
    public ApiResponse<BaguSubmission> update(@AuthenticationPrincipal LoginUser user,
                                              @PathVariable Long id,
                                              @RequestBody BaguSubmission input) {
        return ApiResponse.ok(submissionService.update(user.id(), id, input));
    }

    @Operation(summary = "提交审核（需先完成授权选择）")
    @PutMapping("/my/bagu-submissions/{id}/submit")
    public ApiResponse<BaguSubmission> submit(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return ApiResponse.ok(submissionService.submit(user.id(), id));
    }

    @Operation(summary = "撤回投稿")
    @PutMapping("/my/bagu-submissions/{id}/withdraw")
    public ApiResponse<BaguSubmission> withdraw(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return ApiResponse.ok(submissionService.withdraw(user.id(), id));
    }

    @Operation(summary = "删除投稿（未发布）")
    @DeleteMapping("/my/bagu-submissions/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        submissionService.delete(user.id(), id);
        return ApiResponse.ok();
    }

    // ==================== 公共社区题库 ====================

    @Operation(summary = "社区题库（仅审核通过且授权公开展示）")
    @GetMapping("/bagu/community")
    public ApiResponse<PageResult<Map<String, Object>>> publicPage(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(submissionService.publicPage(category, questionType, keyword, page, size));
    }

    @Operation(summary = "社区题目详情")
    @GetMapping("/bagu/community/{id}")
    public ApiResponse<Map<String, Object>> publicDetail(@PathVariable Long id) {
        return ApiResponse.ok(submissionService.publicDetail(id));
    }

    @Operation(summary = "题目类型选项")
    @GetMapping("/bagu/community/types")
    public ApiResponse<List<String>> types() {
        return ApiResponse.ok(BaguSubmissionService.QUESTION_TYPES);
    }

    // ==================== 管理端 ====================

    @Operation(summary = "管理端：投稿列表")
    @GetMapping("/admin/bagu-submissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<Map<String, Object>>> adminPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(submissionService.adminPage(status, page, size));
    }

    public record ReviewRequest(boolean approve, String comment) {
    }

    @Operation(summary = "管理端：审核投稿（仅授权公开展示的可通过发布）")
    @PutMapping("/admin/bagu-submissions/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BaguSubmission> review(@PathVariable Long id, @RequestBody ReviewRequest request) {
        return ApiResponse.ok(submissionService.review(id, request.approve(), request.comment()));
    }

    @Operation(summary = "管理端：下架已发布投稿")
    @PutMapping("/admin/bagu-submissions/{id}/offline")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BaguSubmission> offline(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ApiResponse.ok(submissionService.offline(id, reason));
    }
}
