package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.common.PageResult;
import com.jobsim.dto.submission.SaveDraftRequest;
import com.jobsim.dto.submission.SubmitRequest;
import com.jobsim.dto.submission.SubmissionDetailVO;
import com.jobsim.dto.submission.SubmissionVO;
import com.jobsim.security.LoginUser;
import com.jobsim.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "提交", description = "保存草稿 / 提交评估 / 我的练习历史")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @Operation(summary = "保存草稿（同一任务覆盖式更新），返回草稿 id")
    @PutMapping("/tasks/{taskId}/draft")
    public ApiResponse<Long> saveDraft(@AuthenticationPrincipal LoginUser user,
                                       @PathVariable Long taskId,
                                       @Valid @RequestBody SaveDraftRequest request) {
        return ApiResponse.ok(submissionService.saveDraft(user.id(), taskId, request));
    }

    @Operation(summary = "提交答案并触发 AI 评估")
    @PostMapping("/tasks/{taskId}/submit")
    public ApiResponse<SubmissionDetailVO> submit(@AuthenticationPrincipal LoginUser user,
                                                  @PathVariable Long taskId,
                                                  @Valid @RequestBody SubmitRequest request) {
        return ApiResponse.ok(submissionService.submit(user.id(), taskId, request));
    }

    @Operation(summary = "我的练习历史（分页）")
    @GetMapping("/my/submissions")
    public ApiResponse<PageResult<SubmissionVO>> myHistory(@AuthenticationPrincipal LoginUser user,
                                                           @RequestParam(required = false) Long page,
                                                           @RequestParam(required = false) Long size) {
        return ApiResponse.ok(submissionService.myHistory(user.id(), page, size));
    }

    @Operation(summary = "提交详情（含评估报告）")
    @GetMapping("/my/submissions/{id}")
    public ApiResponse<SubmissionDetailVO> detail(@AuthenticationPrincipal LoginUser user,
                                                  @PathVariable Long id) {
        return ApiResponse.ok(submissionService.detail(user.id(), id));
    }
}
