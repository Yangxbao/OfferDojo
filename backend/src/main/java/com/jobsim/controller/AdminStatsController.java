package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.common.PageResult;
import com.jobsim.dto.admin.AdminOverviewVO;
import com.jobsim.dto.admin.AiLogVO;
import com.jobsim.mapper.EvaluationMapper;
import com.jobsim.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台-统计与记录", description = "总览 / 评估记录 / AI 调用日志（仅管理员）")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final AdminService adminService;

    @Operation(summary = "总览统计")
    @GetMapping("/overview")
    public ApiResponse<AdminOverviewVO> overview() {
        return ApiResponse.ok(adminService.overview());
    }

    @Operation(summary = "评估记录（分页，可按任务过滤）")
    @GetMapping("/evaluations")
    public ApiResponse<PageResult<EvaluationMapper.EvaluationRow>> evaluations(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(adminService.evaluationPage(taskId, page, size));
    }

    @Operation(summary = "AI 调用日志（可只看失败）")
    @GetMapping("/ai-logs")
    public ApiResponse<PageResult<AiLogVO>> aiLogs(
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(adminService.aiLogPage(success, page, size));
    }
}
