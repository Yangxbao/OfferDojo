package com.jobsim.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobsim.entity.LearningNode;
import com.jobsim.entity.LearningResource;
import com.jobsim.mapper.LearningNodeMapper;
import com.jobsim.mapper.LearningResourceMapper;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 学习资源与路线节点播种（幂等 + 增量）：
 * - learning/learning-resources.json → learning_resource（资源元数据含真实核验结果）
 * - learning/learning-nodes.json → learning_node（三条路线的节点与关联）
 * 按「编码已存在则跳过」写入：管理员后续的审核状态修改不会被重启覆盖；
 * 题库 JSON 扩充后老库重启自动补齐。
 *
 * @Order(LOWEST_PRECEDENCE)：保证在 SchemaMigrationRunner（老库补列）之后执行。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class LearningDataInitializer implements ApplicationRunner {

    private final LearningResourceMapper resourceMapper;
    private final LearningNodeMapper nodeMapper;

    @Value("${jobsim.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        seedResources();
        seedNodes();
    }

    private void seedResources() {
        try (InputStream in = new PathMatchingResourcePatternResolver()
                .getResource("classpath:learning/learning-resources.json").getInputStream()) {
            Set<String> existing = new HashSet<>();
            for (var r : resourceMapper.selectList(Wrappers.<LearningResource>lambdaQuery()
                    .select(LearningResource::getCode))) {
                existing.add(r.getCode());
            }
            JsonNode root = JsonUtils.MAPPER.readTree(in);
            int added = 0, skipped = 0;
            for (JsonNode node : root.get("resources")) {
                String code = node.get("code").asText();
                if (existing.contains(code)) {
                    skipped++;
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
                        ? JsonUtils.toJson(convertList(node.get("prerequisite"))) : "[]");
                r.setVersionNote(node.path("versionNote").asText(null));
                r.setLicenseSpdx(node.path("licenseSpdx").asText(null));
                r.setLicenseName(node.path("licenseName").asText(null));
                r.setLicensePath(node.path("licensePath").asText(null));
                r.setLicenseStatus(node.path("licenseStatus").asText("UNKNOWN"));
                r.setVerifiedStatus(node.path("verifiedStatus").asText("PENDING"));
                r.setVerifiedAt(node.has("verifiedAt") ? LocalDateTime.parse(node.get("verifiedAt").asText()) : null);
                r.setVerificationEvidence(node.has("verificationEvidence")
                        ? node.get("verificationEvidence").toString() : null);
                r.setAllowCopy(node.path("allowCopy").asBoolean(false));
                r.setAllowRedistribute(node.path("allowRedistribute").asBoolean(false));
                r.setAttributionRequired(node.path("attributionRequired").asBoolean(true));
                r.setShareAlike(node.path("shareAlike").asBoolean(false));
                r.setRightsBasis(node.path("rightsBasis").asText(null));
                r.setAdminNote(node.path("adminNote").asText(null));
                resourceMapper.insert(r);
                existing.add(code);
                added++;
            }
            log.info("学习资源初始化完成：本次新增 {} 个，跳过已存在 {} 个", added, skipped);
        } catch (Exception e) {
            throw new IllegalStateException("学习资源初始化失败，请检查 learning/learning-resources.json", e);
        }
    }

    private void seedNodes() {
        try (InputStream in = new PathMatchingResourcePatternResolver()
                .getResource("classpath:learning/learning-nodes.json").getInputStream()) {
            Set<String> existing = new HashSet<>();
            for (var n : nodeMapper.selectList(Wrappers.<LearningNode>lambdaQuery()
                    .select(LearningNode::getCode))) {
                existing.add(n.getCode());
            }
            JsonNode root = JsonUtils.MAPPER.readTree(in);
            int added = 0, skipped = 0;
            for (JsonNode node : root.get("nodes")) {
                String code = node.get("code").asText();
                if (existing.contains(code)) {
                    skipped++;
                    continue;
                }
                LearningNode n = new LearningNode();
                n.setCode(code);
                n.setTrack(node.get("track").asText());
                n.setStageOrder(node.get("stageOrder").asInt());
                n.setNodeOrder(node.path("nodeOrder").asInt(0));
                n.setTitle(node.get("title").asText());
                n.setGoal(node.get("goal").asText());
                n.setKnowledge(JsonUtils.toJson(convertList(node.get("knowledge"))));
                n.setResourceCodes(JsonUtils.toJson(convertList(node.get("resourceCodes"))));
                n.setTaskCodes(JsonUtils.toJson(convertList(node.get("taskCodes"))));
                n.setPrereqCodes(JsonUtils.toJson(convertList(node.get("prereqCodes"))));
                n.setMilestone(node.path("milestone").asText(null));
                nodeMapper.insert(n);
                existing.add(code);
                added++;
            }
            log.info("学习路线节点初始化完成：本次新增 {} 个，跳过已存在 {} 个", added, skipped);
        } catch (Exception e) {
            throw new IllegalStateException("学习路线节点初始化失败，请检查 learning/learning-nodes.json", e);
        }
    }

    private java.util.List<String> convertList(JsonNode node) {
        return node == null ? java.util.List.of()
                : JsonUtils.MAPPER.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                });
    }
}
