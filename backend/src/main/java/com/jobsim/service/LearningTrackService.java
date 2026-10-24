package com.jobsim.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.common.BusinessException;
import com.jobsim.common.ErrorCode;
import com.jobsim.dto.learning.LearningTrackVO;
import com.jobsim.dto.task.TaskVO;
import com.jobsim.entity.LearningNode;
import com.jobsim.entity.LearningPlan;
import com.jobsim.entity.LearningResource;
import com.jobsim.entity.Task;
import com.jobsim.mapper.LearningNodeMapper;
import com.jobsim.mapper.LearningPlanMapper;
import com.jobsim.mapper.LearningResourceMapper;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 三条学习路线（Java 全栈 / AI 全栈 / Python Agent）的组装服务。
 * 与既有十阶段 Java 路线（RoadmapService，面向旧题库）互相独立、互不影响；
 * 节点关联的实战任务解析到任务题库的真实状态（已提交/得分），完成与否不由用户点击声明。
 */
@Service
@RequiredArgsConstructor
public class LearningTrackService {

    private final LearningNodeMapper nodeMapper;
    private final LearningResourceMapper resourceMapper;
    private final LearningPlanMapper planMapper;
    private final TaskMapper taskMapper;
    private final TaskService taskService;

    private static final Map<String, String> TRACK_LABELS = Map.of(
            "java-fullstack", "Java 全栈开发",
            "ai-fullstack", "AI 全栈应用开发",
            "python-agent", "Python Agent 开发");

    private static final Map<String, String> TRACK_DESCRIPTIONS = Map.of(
            "java-fullstack", "从语言地基到企业实战的 Java 后端完整路径（与「学习路线图」的十阶段路线互补，任务同源）",
            "ai-fullstack", "Java 后端工程师转向 LLM 应用开发的路径：API → RAG → Spring AI → Agent → 评估上线",
            "python-agent", "以 Python 与 LangGraph 为核心的 Agent 开发路径，强调受限执行与安全边界");

    /** 单条路线详情（track 为空时返回全部三条的列表由控制器处理） */
    public List<LearningTrackVO> tracks(Long userId, String track) {
        List<LearningNode> nodes = nodeMapper.selectList(Wrappers.<LearningNode>lambdaQuery()
                .eq(track != null && !track.isBlank(), LearningNode::getTrack, track)
                .orderByAsc(LearningNode::getTrack)
                .orderByAsc(LearningNode::getStageOrder)
                .orderByAsc(LearningNode::getNodeOrder));
        if (nodes.isEmpty()) {
            return List.of();
        }

        // 资源与任务批量解析，避免节点级 N+1 查询
        Set<String> resourceCodes = new HashSet<>();
        Set<String> taskCodes = new HashSet<>();
        for (LearningNode node : nodes) {
            resourceCodes.addAll(JsonUtils.readList(node.getResourceCodes(), String.class));
            taskCodes.addAll(JsonUtils.readList(node.getTaskCodes(), String.class));
        }
        Map<String, LearningResource> resourceMap = resourceCodes.isEmpty() ? Map.of()
                : resourceMapper.selectList(Wrappers.<LearningResource>lambdaQuery()
                        .in(LearningResource::getCode, resourceCodes))
                        .stream().collect(Collectors.toMap(LearningResource::getCode, Function.identity()));
        Map<String, Task> taskMap = taskCodes.isEmpty() ? Map.of()
                : taskMapper.selectList(Wrappers.<Task>lambdaQuery().in(Task::getCode, taskCodes))
                        .stream().collect(Collectors.toMap(Task::getCode, Function.identity()));

        // 用户计划状态与任务真实进度
        Map<String, String> planStatusByCode = new HashMap<>();
        if (userId != null) {
            for (LearningPlan p : planMapper.selectList(Wrappers.<LearningPlan>lambdaQuery()
                    .eq(LearningPlan::getUserId, userId)
                    .eq(LearningPlan::getItemType, "NODE"))) {
                planStatusByCode.put(p.getItemCode(), p.getStatus());
            }
        }
        Map<Long, TaskVO> taskVOById = new HashMap<>();
        if (userId != null && !taskMap.isEmpty()) {
            List<Long> ids = new ArrayList<>(taskMap.keySet().stream().map(code ->
                    taskMap.get(code).getId()).toList());
            for (Task t : taskMapper.selectBatchIds(ids)) {
                taskVOById.put(t.getId(), taskService.toTaskVO(t, userId));
            }
        }

        // 按路线分组并组装阶段
        Map<String, List<LearningNode>> byTrack = nodes.stream()
                .collect(Collectors.groupingBy(LearningNode::getTrack, LinkedHashMap::new, Collectors.toList()));
        List<LearningTrackVO> result = new ArrayList<>();
        for (var entry : byTrack.entrySet()) {
            String trackCode = entry.getKey();
            List<LearningNode> trackNodes = entry.getValue();
            Map<Integer, List<LearningNode>> byStage = trackNodes.stream()
                    .collect(Collectors.groupingBy(LearningNode::getStageOrder, java.util.TreeMap::new, Collectors.toList()));

            List<LearningTrackVO.StageVO> stages = new ArrayList<>();
            for (var stageEntry : byStage.entrySet()) {
                List<LearningTrackVO.NodeVO> nodeVOs = new ArrayList<>();
                for (LearningNode node : stageEntry.getValue()) {
                    List<LearningTrackVO.ResourceBrief> resources = JsonUtils
                            .readList(node.getResourceCodes(), String.class).stream()
                            .map(code -> {
                                LearningResource r = resourceMap.get(code);
                                return r == null ? null : new LearningTrackVO.ResourceBrief(
                                        r.getCode(), r.getTitle(), r.getUrl(),
                                        r.getLicenseSpdx(), r.getVerifiedStatus(), r.getSourceType());
                            })
                            .filter(java.util.Objects::nonNull)
                            .toList();
                    List<LearningTrackVO.TaskBrief> tasks = JsonUtils
                            .readList(node.getTaskCodes(), String.class).stream()
                            .map(code -> {
                                Task t = taskMap.get(code);
                                if (t == null) return null;
                                TaskVO vo = taskVOById.get(t.getId());
                                return new LearningTrackVO.TaskBrief(t.getCode(), t.getTitle(), t.getDifficulty(),
                                        vo == null ? "NOT_STARTED" : vo.myStatus(),
                                        vo == null || vo.myBestScore() == null ? null : vo.myBestScore());
                            })
                            .filter(java.util.Objects::nonNull)
                            .toList();
                    nodeVOs.add(new LearningTrackVO.NodeVO(
                            node.getId(), node.getCode(), node.getNodeOrder(),
                            node.getTitle(), node.getGoal(),
                            JsonUtils.readList(node.getKnowledge(), String.class),
                            resources, tasks,
                            JsonUtils.readList(node.getPrereqCodes(), String.class),
                            node.getMilestone(),
                            planStatusByCode.get(node.getCode()), null));
                }
                stages.add(new LearningTrackVO.StageVO(stageEntry.getKey(),
                        "第 " + stageEntry.getKey() + " 阶段", nodeVOs));
            }
            result.add(new LearningTrackVO(trackCode,
                    TRACK_LABELS.getOrDefault(trackCode, trackCode),
                    TRACK_DESCRIPTIONS.getOrDefault(trackCode, ""), stages));
        }
        result.sort(Comparator.comparing(LearningTrackVO::track));
        return result;
    }
}
