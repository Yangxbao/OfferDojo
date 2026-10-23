package com.jobsim.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.dto.roadmap.RoadmapStageVO;
import com.jobsim.entity.Task;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 新手学习路线图服务：读取 classpath:demo/roadmap.json 的阶段定义，
 * 把每个阶段引用的任务编码解析为真实任务（带当前用户的练习状态）。
 * 路线图是"题库之上的策展"：不改任务数据，只决定先做什么、先学什么。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapService {

    private final TaskMapper taskMapper;
    private final TaskService taskService;

    private List<RawStage> stages = List.of();

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource("demo/roadmap.json").getInputStream()) {
            // 文件结构：{"stages":[...]}，先取 stages 字段再转 List
            com.fasterxml.jackson.databind.JsonNode root = JsonUtils.MAPPER.readTree(in);
            stages = JsonUtils.MAPPER.convertValue(root.get("stages"),
                    JsonUtils.MAPPER.getTypeFactory().constructCollectionType(List.class, RawStage.class));
            log.info("学习路线图已加载：{} 个阶段", stages.size());
        } catch (Exception e) {
            log.error("学习路线图加载失败（/api/roadmap 将返回空列表）", e);
            stages = List.of();
        }
    }

    public List<RoadmapStageVO> roadmap(Long userId) {
        // 一次性取出全部已发布任务，按编码索引，避免每个阶段查一次库
        Map<String, Task> byCode = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                        .eq(Task::getStatus, TaskService.STATUS_PUBLISHED))
                .stream()
                .collect(Collectors.toMap(Task::getCode, Function.identity(), (a, b) -> a));

        List<RoadmapStageVO> result = new ArrayList<>();
        for (RawStage stage : stages) {
            List<RoadmapStageVO.TaskBrief> briefs = new ArrayList<>();
            for (String code : stage.taskCodes()) {
                Task task = byCode.get(code);
                if (task == null) {
                    continue; // 题库未包含该编码时优雅跳过，不让路线图报错
                }
                var status = taskService.myStatusMap(userId, List.of(task.getId()));
                var scores = taskService.bestScoreMap(userId, List.of(task.getId()));
                briefs.add(new RoadmapStageVO.TaskBrief(
                        task.getId(), task.getCode(), task.getTitle(), task.getDifficulty(),
                        task.getEstimatedMinutes(),
                        status.getOrDefault(task.getId(), "NOT_STARTED"),
                        scores.get(task.getId())));
            }
            result.add(new RoadmapStageVO(stage.order(), stage.title(), stage.goal(),
                    stage.knowledge(), briefs, stage.milestone()));
        }
        return result;
    }

    /** roadmap.json 的解析载体 */
    public record RawStage(int order, String title, String goal, List<String> knowledge,
                           List<String> taskCodes, String milestone) {
    }
}
