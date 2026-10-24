package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.security.LoginUser;
import com.jobsim.service.CareerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "求职闭环", description = "简历素材生成 / JD 反向匹配")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CareerController {

    private final CareerService careerService;

    public record JdRequest(
            @NotBlank(message = "岗位描述不能为空")
            @Size(max = 10000, message = "岗位描述最长 10000 字")
            String text
    ) {
    }

    @Operation(summary = "简历素材草稿（基于真实练习记录生成，需人工改写）")
    @GetMapping("/my/resume-material")
    public ApiResponse<Map<String, Object>> resumeMaterial(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(careerService.resumeMaterial(user.id()));
    }

    @Operation(summary = "JD 反向匹配：粘贴岗位描述，推荐需要补的任务与八股")
    @PostMapping("/jd/match")
    public ApiResponse<Map<String, Object>> matchJd(@Valid @RequestBody JdRequest request) {
        return ApiResponse.ok(careerService.matchJd(request.text()));
    }
}
