package com.jobsim.controller;

import com.jobsim.common.ApiResponse;
import com.jobsim.dto.learning.LearningResourceVO;
import com.jobsim.entity.LearningResource;
import com.jobsim.mapper.LearningResourceMapper;
import com.jobsim.service.GitHubMetadataService;
import com.jobsim.service.LearningResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 资源中心管理端：新增/编辑/重新核验/上下架。
 * 双重防护：SecurityConfig 对 /api/admin/** 的路径拦截 + 本类 @PreAuthorize。
 */
@Tag(name = "后台-资源中心", description = "学习资源的登记、审核与重新核验（仅管理员）")
@RestController
@RequestMapping("/api/admin/learning/resources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LearningAdminController {

    private final LearningResourceMapper resourceMapper;
    private final LearningResourceService resourceService;
    private final GitHubMetadataService gitHubMetadataService;

    @Data
    public static class ResourceSaveRequest {
        @NotBlank(message = "资源编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9-]{2,64}$", message = "编码只能包含字母数字与中划线")
        private String code;

        @NotBlank(message = "技术方向不能为空")
        @Pattern(regexp = "^(java-fullstack|ai-fullstack|python-agent)$", message = "技术方向不合法")
        private String track;

        @NotBlank(message = "标题不能为空")
        @Size(max = 300, message = "标题最长 300 字")
        private String title;

        @NotBlank(message = "简介不能为空")
        @Size(max = 1000, message = "简介最长 1000 字")
        private String description;

        @NotBlank(message = "原始链接不能为空")
        @Size(max = 500, message = "链接最长 500 字符")
        private String url;

        @NotBlank(message = "资源类型不能为空")
        private String sourceType;

        @Size(max = 300, message = "适用人群最长 300 字")
        private String audience;

        private java.util.List<String> prerequisite;

        @Size(max = 300, message = "版本提示最长 300 字")
        private String versionNote;

        @Size(max = 64, message = "SPDX 标识最长 64 字符")
        private String licenseSpdx;

        @Size(max = 200, message = "许可证名称最长 200 字")
        private String licenseName;

        @Size(max = 300, message = "许可证路径最长 300 字")
        private String licensePath;

        @Pattern(regexp = "^(UNKNOWN|VERIFIED|NOASSERTION)$", message = "许可证状态不合法")
        private String licenseStatus;

        @Pattern(regexp = "^(PENDING|VERIFIED|PUBLISHED|OFFLINE)$", message = "审核状态不合法")
        private String verifiedStatus;

        @Size(max = 500, message = "权限依据最长 500 字")
        private String rightsBasis;

        @Size(max = 500, message = "管理员备注最长 500 字")
        private String adminNote;
    }

    @Operation(summary = "登记新资源（初始状态为待核验，权限字段默认禁止）")
    @PostMapping
    public ApiResponse<LearningResourceVO> create(@Valid @RequestBody ResourceSaveRequest request) {
        LearningResource resource = new LearningResource();
        applyRequest(resource, request);
        // 新登记资源一律进入待核验；使用权限默认全部禁止，由管理员核验后依据证据开放
        resource.setVerifiedStatus(LearningResource.STATUS_PENDING);
        resource.setLicenseStatus(request.getLicenseStatus() == null ? "UNKNOWN" : request.getLicenseStatus());
        resource.setAllowCopy(false);
        resource.setAllowRedistribute(false);
        resource.setAttributionRequired(true);
        resource.setShareAlike(false);
        resource.setRightsBasis("新登记资源，权限未核验，默认禁止复制与再分发");
        resourceMapper.insert(resource);
        return ApiResponse.ok(resourceService.toVO(resource, false));
    }

    @Operation(summary = "编辑资源元数据（不改审核状态，状态走独立接口）")
    @PutMapping("/{id}")
    public ApiResponse<LearningResourceVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody ResourceSaveRequest request) {
        LearningResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            return ApiResponse.fail(404, "资源不存在");
        }
        applyRequest(resource, request);
        resourceMapper.updateById(resource);
        return ApiResponse.ok(resourceService.toVO(resource, null));
    }

    @Operation(summary = "重新核验：调用 GitHub API 获取元数据，失败保持待核验并记录原因")
    @PostMapping("/{id}/reverify")
    public ApiResponse<Map<String, Object>> reverify(@PathVariable Long id) {
        LearningResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            return ApiResponse.fail(404, "资源不存在");
        }
        Map<String, Object> verifyResult = gitHubMetadataService.verify(resource.getUrl());
        resource.setVerifiedAt(LocalDateTime.now());
        // 无论成败都把本次核验证据追加记录（可追溯）
        String evidence = String.valueOf(verifyResult.get("success")) + " @ " + LocalDateTime.now()
                + " via GitHub API; " + verifyResult.get("message");
        resource.setVerificationEvidence(evidence);

        if (Boolean.TRUE.equals(verifyResult.get("success"))) {
            // 元数据核验成功：刷新许可证与版本信息；审核状态只推进到 VERIFIED，
            // PUBLISHED（可展示）必须由管理员依据内容审核人工设置，不自动提升
            resource.setVerifiedStatus(LearningResource.STATUS_VERIFIED);
            resource.setLicenseSpdx(String.valueOf(verifyResult.get("licenseSpdx")));
            resource.setLicenseName(String.valueOf(verifyResult.get("licenseName")));
            resource.setLicenseStatus(verifyResult.get("licenseSpdx") == null ? "UNKNOWN" : "VERIFIED");
        } else {
            // 核验失败：不改变现有状态，仅记录原因（保留 evidence）
            resource.setAdminNote("重新核验失败：" + verifyResult.get("message"));
        }
        resourceMapper.updateById(resource);
        verifyResult.put("resource", resourceService.toVO(resource, null));
        return ApiResponse.ok(verifyResult);
    }

    @Operation(summary = "设置审核状态（含下架/恢复；设为可展示需管理员对内容审核负责）")
    @PutMapping("/{id}/status")
    public ApiResponse<LearningResourceVO> updateStatus(@PathVariable Long id,
                                                        @org.springframework.web.bind.annotation.RequestParam String status) {
        LearningResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            return ApiResponse.fail(404, "资源不存在");
        }
        if (!LearningResource.STATUS_PENDING.equals(status) && !LearningResource.STATUS_VERIFIED.equals(status)
                && !LearningResource.STATUS_PUBLISHED.equals(status) && !LearningResource.STATUS_OFFLINE.equals(status)) {
            return ApiResponse.fail(400, "状态不合法");
        }
        resource.setVerifiedStatus(status);
        resource.setVerifiedAt(LocalDateTime.now());
        resourceMapper.updateById(resource);
        return ApiResponse.ok(resourceService.toVO(resource, null));
    }

    @Operation(summary = "删除资源（仅限误登记的清理；核验记录随之移除）")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resourceMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private void applyRequest(LearningResource resource, ResourceSaveRequest request) {
        resource.setCode(request.getCode());
        resource.setTrack(request.getTrack());
        resource.setTitle(request.getTitle());
        resource.setDescription(request.getDescription());
        resource.setUrl(request.getUrl());
        resource.setSourceType(request.getSourceType());
        resource.setAudience(request.getAudience());
        resource.setPrerequisite(request.getPrerequisite() == null ? "[]"
                : com.jobsim.util.JsonUtils.toJson(request.getPrerequisite()));
        resource.setVersionNote(request.getVersionNote());
        resource.setLicenseSpdx(request.getLicenseSpdx());
        resource.setLicenseName(request.getLicenseName());
        resource.setLicensePath(request.getLicensePath());
        if (request.getLicenseStatus() != null) {
            resource.setLicenseStatus(request.getLicenseStatus());
        }
        resource.setRightsBasis(request.getRightsBasis());
        resource.setAdminNote(request.getAdminNote());
    }
}
