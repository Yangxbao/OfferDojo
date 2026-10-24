package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.common.PageResult;
import com.jobsim.dto.learning.LearningResourceVO;
import com.jobsim.security.LoginUser;
import com.jobsim.service.LearningResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "资源中心", description = "开源学习资源的浏览、筛选与详情（核验状态与权限透明展示）")
@RestController
@RequestMapping("/api/learning/resources")
@RequiredArgsConstructor
public class LearningResourceController {

    private final LearningResourceService resourceService;

    @Operation(summary = "资源列表（方向/类型/许可证状态/审核状态/关键词筛选）")
    @GetMapping
    public ApiResponse<PageResult<LearningResourceVO>> page(
            @RequestParam(required = false) String track,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String licenseStatus,
            @RequestParam(required = false) String verifiedStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(resourceService.page(track, sourceType, licenseStatus,
                verifiedStatus, keyword, page, size));
    }

    @Operation(summary = "资源详情（含核验证据与使用权限）")
    @GetMapping("/{id}")
    public ApiResponse<LearningResourceVO> detail(@AuthenticationPrincipal LoginUser user,
                                                  @PathVariable Long id) {
        return ApiResponse.ok(resourceService.detail(user == null ? null : user.id(), id));
    }
}
