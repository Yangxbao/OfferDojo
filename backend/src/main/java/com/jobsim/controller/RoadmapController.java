package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.dto.roadmap.RoadmapStageVO;
import com.jobsim.security.LoginUser;
import com.jobsim.service.RoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "学习路线图", description = "新手路线：从零基础到企业实战的分阶段练习路径")
@RestController
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapService roadmapService;

    @Operation(summary = "学习路线图（按阶段返回推荐任务与当前进度）")
    @GetMapping("/api/roadmap")
    public ApiResponse<List<RoadmapStageVO>> roadmap(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(roadmapService.roadmap(user.id()));
    }
}
