package com.jobsim.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobsim.dto.task.RubricItem;
import com.jobsim.entity.Task;
import com.jobsim.entity.User;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.mapper.UserMapper;
import com.jobsim.service.AuthService;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 演示数据初始化（幂等 + 增量）：
 * - 演示账号：仅在用户表为空时写入（admin/admin123 管理员、student/student123 学生，BCrypt 哈希存储）；
 * - 内置任务：加载 classpath:demo/demo-tasks*.json 的全部题库文件，
 *   按「任务编码已存在则跳过」的方式增量写入——题库扩充后，老库重启即可自动补齐新题。
 *
 * @Order(LOWEST_PRECEDENCE)：保证在 SchemaMigrationRunner（老库补列）之后执行。
 * 生产环境应设置 jobsim.seed.enabled=false 并自行管理数据。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${jobsim.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("演示数据初始化已关闭（jobsim.seed.enabled=false）");
            return;
        }
        seedUsers();
        seedTasks();
    }

    private void seedUsers() {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        createUser("admin", "admin123", AuthService.ROLE_ADMIN, "管理员");
        createUser("student", "student123", AuthService.ROLE_USER, "演示学生");
        log.info("已写入演示账号：admin / admin123（管理员）、student / student123（学生）。仅用于本地演示，部署前请修改密码或关闭 seed。");
    }

    private void createUser(String username, String password, String role, String nickname) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setNickname(nickname);
        userMapper.insert(user);
    }

    /**
     * 增量播种任务：读取 demo/ 下全部 demo-tasks*.json，
     * 与库中已有任务按编码比对，只插入新编码。已初始化过的库重启后自动补齐新题。
     */
    private void seedTasks() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:demo/demo-tasks*.json");
            // 文件名排序保证播种顺序稳定
            java.util.Arrays.sort(files, java.util.Comparator.comparing(Resource::getFilename,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

            Set<String> existingCodes = new HashSet<>();
            for (Task t : taskMapper.selectList(Wrappers.<Task>lambdaQuery().select(Task::getCode))) {
                existingCodes.add(t.getCode());
            }
            Integer maxOrder = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                            .select(Task::getSortOrder).isNotNull(Task::getSortOrder)
                            .orderByDesc(Task::getSortOrder).last("LIMIT 1"))
                    .stream().findFirst().map(Task::getSortOrder).orElse(-1);

            int added = 0;
            int skipped = 0;
            int order = maxOrder + 1;
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    JsonNode root = JsonUtils.MAPPER.readTree(in);
                    for (JsonNode node : root.get("tasks")) {
                        String code = node.get("code").asText();
                        if (existingCodes.contains(code)) {
                            skipped++;
                            continue;
                        }
                        Task task = buildTask(node, order++);
                        taskMapper.insert(task);
                        existingCodes.add(code);
                        added++;
                    }
                }
            }
            log.info("内置任务初始化完成：题库文件 {} 个，本次新增 {} 个，跳过已存在 {} 个",
                    files.length, added, skipped);
        } catch (Exception e) {
            throw new IllegalStateException("内置任务初始化失败，请检查 demo/demo-tasks*.json 是否完整", e);
        }
    }

    private Task buildTask(JsonNode node, int sortOrder) {
        Task task = new Task();
        task.setCode(node.get("code").asText());
        task.setTitle(node.get("title").asText());
        task.setCategory(node.get("category").asText());
        task.setDifficulty(node.get("difficulty").asText());
        task.setTags(node.get("tags").asText());
        task.setSummary(node.get("summary").asText());
        task.setEstimatedMinutes(node.get("estimatedMinutes").asInt(30));
        task.setBackground(node.get("background").asText());
        task.setRequirements(JsonUtils.toJson(convertList(node.get("requirements"))));
        task.setConstraintItems(JsonUtils.toJson(convertList(node.get("constraints"))));
        task.setDeliverables(JsonUtils.toJson(convertList(node.get("deliverables"))));
        task.setAcceptance(JsonUtils.toJson(convertList(node.get("acceptance"))));
        task.setRubricJson(JsonUtils.toJson(
                JsonUtils.MAPPER.convertValue(node.get("rubric"), new TypeReference<List<RubricItem>>() {
                })));
        task.setMockHintsJson(node.get("mockHints") == null ? null : node.get("mockHints").toString());
        task.setStatus("PUBLISHED");
        task.setSortOrder(sortOrder);
        return task;
    }

    private List<String> convertList(JsonNode node) {
        return node == null ? List.of() : JsonUtils.MAPPER.convertValue(node, new TypeReference<List<String>>() {
        });
    }
}
