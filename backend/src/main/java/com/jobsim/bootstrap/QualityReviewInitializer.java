package com.jobsim.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobsim.entity.LearningResource;
import com.jobsim.mapper.LearningResourceMapper;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源质量评审播种（第三阶段）：
 * - reviews：为已完成人工评审的资源回填质量分/等级/精选/实时 GitHub 指标。
 *   只在 quality_score IS NULL（即评审数据尚未落库且管理员也未评过）时写入，
 *   绝不覆盖管理员在页面上维护过的评分与状态。
 * - newResources：新增官方文档资源（按编码增量，跳过已存在）。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class QualityReviewInitializer implements ApplicationRunner {

    private final LearningResourceMapper resourceMapper;

    @Value("${jobsim.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        try (InputStream in = new ClassPathResource("learning/quality-reviews.json").getInputStream()) {
            JsonNode root = JsonUtils.MAPPER.readTree(in);

            // ===== 1. 评审回填：仅填 quality_score 为空的行 =====
            Map<String, LearningResource> byCode = new HashMap<>();
            for (var r : resourceMapper.selectList(Wrappers.<LearningResource>lambdaQuery())) {
                byCode.put(r.getCode(), r);
            }
            int reviewed = 0;
            for (JsonNode review : root.get("reviews")) {
                LearningResource existing = byCode.get(review.get("code").asText());
                if (existing == null || existing.getQualityScore() != null) {
                    continue; // 不存在或已有评分（含管理员评审）→ 跳过
                }
                existing.setQualityScore(review.get("qualityScore").asInt());
                existing.setQualityLevel(review.get("qualityLevel").asText());
                existing.setFeatured(review.get("featured").asInt() == 1);
                if (!review.get("stars").isNull()) existing.setStarsCount(review.get("stars").asLong());
                if (!review.get("forks").isNull()) existing.setForksCount(review.get("forks").asLong());
                if (!review.get("pushedAt").isNull()) {
                    existing.setGithubPushedAt(LocalDateTime.parse(
                            OffsetDateTime.parse(review.get("pushedAt").asText()).toString().substring(0, 19)));
                }
                existing.setReviewNote(review.get("reviewNote").asText());
                existing.setVerifiedAt(LocalDateTime.now());
                // OFFLINE 评审同步下架状态；其余保持原审核状态（PUBLISHED/VERIFIED/PENDING）
                if ("OFFLINE".equals(review.get("qualityLevel").asText())) {
                    existing.setVerifiedStatus(LearningResource.STATUS_OFFLINE);
                }
                resourceMapper.updateById(existing);
                reviewed++;
            }

            // ===== 2. 新增官方资源：按编码增量 =====
            int added = 0;
            for (JsonNode node : root.get("newResources")) {
                String code = node.get("code").asText();
                if (byCode.containsKey(code)) {
                    continue;
                }
                LearningResource r = new LearningResource();
                r.setCode(code);
                r.setTrack(node.get("track").asText());
                r.setTitle(node.get("title").asText());
                r.setDescription(node.get("description").asText());
                r.setUrl(node.get("url").asText());
                r.setSourceType(node.get("sourceType").asText());
                r.setAudience(node.path("audience").asText(null));
                r.setPrerequisite(node.has("prerequisite")
                        ? JsonUtils.toJson(JsonUtils.MAPPER.convertValue(node.get("prerequisite"),
                                JsonUtils.MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, String.class)))
                        : "[]");
                r.setVersionNote(node.path("versionNote").asText(null));
                r.setLicenseSpdx(node.path("licenseSpdx").isNull() ? null : node.path("licenseSpdx").asText());
                r.setLicenseName(node.path("licenseName").asText(null));
                r.setLicenseStatus(node.path("licenseStatus").asText("UNKNOWN"));
                r.setVerifiedStatus(node.path("verifiedStatus").asText("PENDING"));
                r.setVerifiedAt(LocalDateTime.now());
                r.setLanguage(node.path("language").asText(null));
                r.setDifficultyLevel(node.path("difficultyLevel").asText(null));
                r.setQualityScore(node.has("qualityScore") ? node.get("qualityScore").asInt() : null);
                r.setQualityLevel(node.path("qualityLevel").asText(null));
                r.setFeatured(node.path("featured").asInt() == 1);
                r.setReviewNote(node.path("reviewNote").asText(null));
                // 权限默认禁止（官方文档条款未核验前不开复制/再分发）
                r.setAllowCopy(false);
                r.setAllowRedistribute(false);
                r.setAttributionRequired(true);
                r.setShareAlike(false);
                r.setRightsBasis("官方文档：仅展示元数据与链接；复制/再分发条款未核验前默认禁止");
                resourceMapper.insert(r);
                added++;
            }
            log.info("质量评审播种完成：评审回填 {} 条，新增官方资源 {} 条", reviewed, added);
        } catch (Exception e) {
            throw new IllegalStateException("质量评审数据加载失败，请检查 learning/quality-reviews.json", e);
        }
    }
}
