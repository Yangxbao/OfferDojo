package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.dto.followup.FollowupDialogVO;
import com.jobsim.security.LoginUser;
import com.jobsim.service.FollowupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "追问模拟面试", description = "对评估报告中的技术追问作答，AI 按上下文给出反馈")
@RestController
@RequiredArgsConstructor
public class FollowupController {

    private final FollowupService followupService;

    public record AnswerRequest(
            @NotBlank(message = "追问问题不能为空")
            @Size(max = 500, message = "追问问题最长 500 字")
            String question,

            @NotBlank(message = "回答内容不能为空")
            @Size(max = 10000, message = "回答最长 10000 字")
            String content
    ) {
    }

    @Operation(summary = "某评估的追问对话记录")
    @GetMapping("/api/my/evaluations/{evaluationId}/dialog")
    public ApiResponse<List<FollowupDialogVO>> list(@AuthenticationPrincipal LoginUser user,
                                                    @PathVariable Long evaluationId) {
        return ApiResponse.ok(followupService.list(user.id(), evaluationId));
    }

    @Operation(summary = "回答一个追问，返回 AI 反馈")
    @PostMapping("/api/my/evaluations/{evaluationId}/dialog")
    public ApiResponse<FollowupDialogVO> answer(@AuthenticationPrincipal LoginUser user,
                                                @PathVariable Long evaluationId,
                                                @Valid @RequestBody AnswerRequest request) {
        return ApiResponse.ok(followupService.answer(user.id(), evaluationId,
                request.question(), request.content()));
    }
}
