package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.dto.dashboard.DashboardVO;
import com.jobsim.dto.progress.ProgressVO;
import com.jobsim.security.LoginUser;
import com.jobsim.service.DashboardService;
import com.jobsim.service.ProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "工作台与能力分析", description = "首页数据面板 / 学习统计")
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final ProgressService progressService;

    @Operation(summary = "工作台数据（今日任务/统计/能力/推荐/继续上次）")
    @GetMapping("/api/dashboard")
    public ApiResponse<DashboardVO> dashboard(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(dashboardService.dashboard(user.id()));
    }

    @Operation(summary = "能力分析（趋势/维度/薄弱环节/建议/完成率）")
    @GetMapping("/api/my/progress")
    public ApiResponse<ProgressVO> progress(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(progressService.progress(user.id()));
    }
}
