package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.dto.learning.PlanItemVO;
import com.jobsim.security.LoginUser;
import com.jobsim.service.LearningPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

@Tag(name = "我的学习计划", description = "加入/移除资源与节点，记录学习状态与心得（与登录用户关联）")
@RestController
@RequestMapping("/api/learning/plan")
@RequiredArgsConstructor
public class LearningPlanController {

    private final LearningPlanService planService;

    public record AddRequest(
            @NotBlank(message = "条目类型不能为空") String itemType,
            @NotBlank(message = "条目编码不能为空") String itemCode
    ) {
    }

    public record UpdateRequest(String status, String note) {
    }

    @Operation(summary = "我的学习计划列表（可按状态过滤）")
    @GetMapping
    public ApiResponse<List<PlanItemVO>> myPlan(@AuthenticationPrincipal LoginUser user,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(planService.myPlan(user.id(), status));
    }

    @Operation(summary = "加入学习计划（资源或路线节点）")
    @PostMapping
    public ApiResponse<PlanItemVO> add(@AuthenticationPrincipal LoginUser user,
                                       @Valid @RequestBody AddRequest request) {
        return ApiResponse.ok(planService.add(user.id(), request.itemType(), request.itemCode()));
    }

    @Operation(summary = "更新学习状态与心得")
    @PutMapping("/{id}")
    public ApiResponse<PlanItemVO> update(@AuthenticationPrincipal LoginUser user,
                                          @PathVariable Long id,
                                          @RequestBody UpdateRequest request) {
        return ApiResponse.ok(planService.updateStatus(user.id(), id, request.status(), request.note()));
    }

    @Operation(summary = "移出学习计划")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal LoginUser user, @PathVariable Long id) {
        planService.remove(user.id(), id);
        return ApiResponse.ok();
    }
}
