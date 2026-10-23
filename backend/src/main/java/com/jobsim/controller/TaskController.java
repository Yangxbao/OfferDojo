package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.common.PageResult;
import com.jobsim.dto.task.TaskDetailVO;
import com.jobsim.dto.task.TaskVO;
import com.jobsim.security.LoginUser;
import com.jobsim.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "任务", description = "任务浏览（用户视角，仅展示已发布任务）")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "任务列表（分页 + 筛选）")
    @GetMapping
    public ApiResponse<PageResult<TaskVO>> page(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(taskService.pageForUser(user.id(), category, difficulty, keyword, page, size));
    }

    @Operation(summary = "任务详情（含我的草稿与练习状态）")
    @GetMapping("/{id}")
    public ApiResponse<TaskDetailVO> detail(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        return ApiResponse.ok(taskService.detailForUser(user.id(), id));
    }
}
