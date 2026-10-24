package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.dto.learning.LearningTrackVO;
import com.jobsim.security.LoginUser;
import com.jobsim.service.LearningTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "学习路线（三条方向）", description = "Java 全栈 / AI 全栈 / Python Agent 的阶段化学习路线，节点关联资源与实战任务")
@RestController
@RequiredArgsConstructor
public class LearningTrackController {

    private final LearningTrackService trackService;

    @Operation(summary = "学习路线（track 为空返回全部三条；节点含用户进度与任务真实验收状态）")
    @GetMapping("/api/learning/tracks")
    public ApiResponse<List<LearningTrackVO>> tracks(@AuthenticationPrincipal LoginUser user,
                                                     @RequestParam(required = false) String track) {
        return ApiResponse.ok(trackService.tracks(user == null ? null : user.id(), track));
    }
}
