package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.common.PageResult;
import com.jobsim.dto.bagu.BaguVO;
import com.jobsim.service.BaguService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "八股复习", description = "高频面试知识题库：一句话总结 + 大白话解释 + 背诵要点 + 示例")
@RestController
@RequestMapping("/api/bagu")
@RequiredArgsConstructor
public class BaguController {

    private final BaguService baguService;

    @Operation(summary = "八股题目分页检索（类目 / 频率 / 关键词）")
    @GetMapping("/questions")
    public ApiResponse<PageResult<BaguVO>> questions(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String frequency,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {
        return ApiResponse.ok(baguService.page(category, frequency, keyword, page, size));
    }

    @Operation(summary = "类目统计（含题数）")
    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(baguService.categories());
    }
}
