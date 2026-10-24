package com.jobsim.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobsim.entity.InterviewQuestion;
import com.jobsim.mapper.InterviewQuestionMapper;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * 八股题库播种（幂等 + 增量）：加载 classpath:bagu/bagu-*.json，
 * 按「题目编码已存在则跳过」的方式写入 interview_question 表。
 * 题库文件扩充后，老库重启即自动补齐。
 *
 * @Order(LOWEST_PRECEDENCE)：保证在 SchemaMigrationRunner（老库补列）之后执行。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class BaguDataInitializer implements ApplicationRunner {

    private final InterviewQuestionMapper questionMapper;

    @Value("${jobsim.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            return;
        }
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:bagu/bagu-*.json");
            java.util.Arrays.sort(files, java.util.Comparator.comparing(Resource::getFilename,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

            Set<String> existing = new HashSet<>();
            for (var q : questionMapper.selectList(Wrappers.<InterviewQuestion>lambdaQuery()
                    .select(InterviewQuestion::getCode))) {
                existing.add(q.getCode());
            }

            int added = 0;
            int skipped = 0;
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    JsonNode root = JsonUtils.MAPPER.readTree(in);
                    for (JsonNode node : root.get("questions")) {
                        String code = node.get("code").asText();
                        if (existing.contains(code)) {
                            skipped++;
                            continue;
                        }
                        InterviewQuestion q = new InterviewQuestion();
                        q.setCode(code);
                        q.setCategory(node.get("category").asText());
                        q.setTitle(node.get("title").asText());
                        q.setFrequency(node.path("frequency").asText("MID"));
                        q.setDifficulty(node.path("difficulty").asText("EASY"));
                        q.setTags(node.path("tags").asText(""));
                        q.setOneLiner(node.get("oneLiner").asText());
                        q.setPlain(node.get("plain").asText());
                        q.setPoints(JsonUtils.toJson(convertList(node.get("points"))));
                        q.setExample(node.hasNonNull("example") ? node.get("example").asText() : null);
                        q.setFollowUps(node.hasNonNull("followUps")
                                ? JsonUtils.toJson(convertList(node.get("followUps"))) : null);
                        q.setVersionNote(node.hasNonNull("versionNote") ? node.get("versionNote").asText() : null);
                        questionMapper.insert(q);
                        existing.add(code);
                        added++;
                    }
                }
            }
            log.info("八股题库初始化完成：题库文件 {} 个，本次新增 {} 个，跳过已存在 {} 个",
                    files.length, added, skipped);
        } catch (Exception e) {
            throw new IllegalStateException("八股题库初始化失败，请检查 bagu/bagu-*.json 是否完整", e);
        }
    }

    private java.util.List<String> convertList(JsonNode node) {
        return node == null ? java.util.List.of()
                : JsonUtils.MAPPER.convertValue(node, new TypeReference<java.util.List<String>>() {
                });
    }
}
