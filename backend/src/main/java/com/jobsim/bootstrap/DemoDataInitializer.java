package com.jobsim.bootstrap;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 演示数据初始化（幂等）：仅在对应表为空时写入。
 * - 演示账号：admin/admin123（管理员）、student/student123（学生），密码 BCrypt 哈希存储
 * - 内置任务：classpath:demo/demo-tasks.json 中的 12 个模拟任务
 * 生产环境应设置 jobsim.seed.enabled=false 并自行管理数据。
 */
@Slf4j
@Component
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

    private void seedTasks() {
        if (taskMapper.selectCount(null) > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource("demo/demo-tasks.json").getInputStream()) {
            JsonNode root = JsonUtils.MAPPER.readTree(in);
            int order = 0;
            for (JsonNode node : root.get("tasks")) {
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
                task.setSortOrder(order++);
                taskMapper.insert(task);
            }
            log.info("已写入 {} 个内置模拟任务", order);
        } catch (Exception e) {
            // 演示数据写入失败意味着系统不可用，快速失败并给出明确原因
            throw new IllegalStateException("内置任务初始化失败，请检查 demo/demo-tasks.json 是否完整", e);
        }
    }

    private List<String> convertList(JsonNode node) {
        return node == null ? List.of() : JsonUtils.MAPPER.convertValue(node, new TypeReference<List<String>>() {
        });
    }
}
