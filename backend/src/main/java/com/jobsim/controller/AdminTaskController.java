package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.common.PageResult;
import com.jobsim.dto.admin.AdminTaskVO;
import com.jobsim.dto.task.TaskDetailVO;
import com.jobsim.dto.task.TaskSaveRequest;
import com.jobsim.dto.task.TaskVO;
import com.jobsim.entity.Task;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.security.LoginUser;
import com.jobsim.service.AdminService;
import com.jobsim.service.TaskService;
import com.jobsim.util.JsonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台任务管理。双重防护：
 * 1) SecurityConfig 对 /api/admin/** 做了 hasRole('ADMIN') 的路径级拦截；
 * 2) 这里再加 @PreAuthorize，即使路径规则被误改也不会放行普通用户。
 */
@Tag(name = "后台-任务管理", description = "任务新建/编辑/上下架（仅管理员）")
@RestController
@RequestMapping("/api/admin/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTaskController {

    private final TaskService taskService;
    private final TaskMapper taskMapper;

    @Operation(summary = "任务列表（含未发布/已下架，附练习统计）")
    @GetMapping
    public ApiResponse<PageResult<AdminTaskVO>> page(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(taskService.adminPage(category, difficulty, status, keyword, page, size));
    }

    @Operation(summary = "任务详情（编辑回显，含评分标准与提示数据）")
    @GetMapping("/{id}")
    public ApiResponse<TaskSaveRequest> detail(@PathVariable Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            return ApiResponse.fail(404, "任务不存在");
        }
        TaskSaveRequest req = new TaskSaveRequest();
        req.setCode(task.getCode());
        req.setTitle(task.getTitle());
        req.setCategory(task.getCategory());
        req.setDifficulty(task.getDifficulty());
        req.setTags(task.getTags() == null || task.getTags().isBlank()
                ? List.of() : List.of(task.getTags().split(",")));
        req.setSummary(task.getSummary());
        req.setEstimatedMinutes(task.getEstimatedMinutes());
        req.setBackground(task.getBackground());
        req.setRequirements(JsonUtils.readList(task.getRequirements(), String.class));
        req.setConstraintItems(JsonUtils.readList(task.getConstraintItems(), String.class));
        req.setDeliverables(JsonUtils.readList(task.getDeliverables(), String.class));
        req.setAcceptance(JsonUtils.readList(task.getAcceptance(), String.class));
        req.setRubric(JsonUtils.readList(task.getRubricJson(), com.jobsim.dto.task.RubricItem.class));
        req.setStatus(task.getStatus());
        req.setSortOrder(task.getSortOrder());
        return ApiResponse.ok(req);
    }

    @Operation(summary = "新建任务")
    @PostMapping
    public ApiResponse<TaskVO> create(@RequestBody @Valid TaskSaveRequest request) {
        Task task = taskService.create(request);
        return ApiResponse.ok(taskService.toTaskVO(task, null));
    }

    @Operation(summary = "编辑任务")
    @PutMapping("/{id}")
    public ApiResponse<TaskVO> update(@PathVariable Long id, @RequestBody @Valid TaskSaveRequest request) {
        Task task = taskService.update(id, request);
        return ApiResponse.ok(taskService.toTaskVO(task, null));
    }

    @Operation(summary = "上架 / 下架 / 转草稿")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        taskService.updateStatus(id, status);
        return ApiResponse.ok();
    }
}
