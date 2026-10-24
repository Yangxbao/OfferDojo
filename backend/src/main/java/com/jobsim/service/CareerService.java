package com.jobsim.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.dto.roadmap.RoadmapStageVO;
import com.jobsim.dto.task.TaskVO;
import com.jobsim.dto.progress.AbilityVO;
import com.jobsim.entity.Evaluation;
import com.jobsim.entity.Task;
import com.jobsim.mapper.EvaluationMapper;
import com.jobsim.mapper.InterviewQuestionMapper;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 求职闭环服务：
 * 1. 简历素材生成——基于真实练习记录（已完成任务 + 评估得分 + 亮点）生成可改写的草稿；
 * 2. JD 反向匹配——解析目标岗位描述文本，按任务/八股的标签与关键词命中推荐补强内容。
 * 两者都是"规则生成 + 明确标注来源"，不虚构用户没有的经历。
 */
@Service
@RequiredArgsConstructor
public class CareerService {

    private final EvaluationMapper evaluationMapper;
    private final TaskMapper taskMapper;
    private final com.jobsim.mapper.SubmissionMapper submissionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final TaskService taskService;

    /** 简历素材：全部内容来自用户真实练习与评估数据 */
    public Map<String, Object> resumeMaterial(Long userId) {
        List<Evaluation> evaluations = evaluationMapper.selectList(Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getUserId, userId)
                .orderByDesc(Evaluation::getCreatedAt));
        List<AbilityVO> abilities = com.jobsim.service.AbilityCalculator.aggregate(evaluations);

        // 每个任务取最高分的那次评估
        Map<Long, Evaluation> bestByTask = new LinkedHashMap<>();
        for (Evaluation e : evaluations) {
            bestByTask.merge(e.getTaskId(), e, (a, b) ->
                    a.getTotalScore().doubleValue() >= b.getTotalScore().doubleValue() ? a : b);
        }

        List<Long> taskIds = new ArrayList<>(bestByTask.keySet());
        Map<Long, Task> taskMap = taskIds.isEmpty() ? Map.of()
                : taskMapper.selectBatchIds(taskIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Task::getId, t -> t));

        // 技能清单：按平均分分层（明确说明这是"练习评估"口径，不是绝对能力）
        List<String> skillLines = new ArrayList<>();
        for (AbilityVO a : abilities) {
            if (a.practiceCount() == 0) {
                continue;
            }
            String level = a.avgScore() >= 8 ? "较熟练" : a.avgScore() >= 6 ? "基本掌握" : "待加强";
            skillLines.add("- %s：%s（练习 %d 次，平均 %.1f/10）".formatted(a.dimension(), level,
                    a.practiceCount(), a.avgScore()));
        }

        // 项目/实战经历条目（练习口径，需人工改写）
        List<String> experienceLines = new ArrayList<>();
        bestByTask.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        b.getValue().getTotalScore().doubleValue(), a.getValue().getTotalScore().doubleValue()))
                .forEach(entry -> {
                    Evaluation e = entry.getValue();
                    Task task = taskMap.get(entry.getKey());
                    if (task == null) {
                        return;
                    }
                    List<String> strengths = JsonUtils.readList(e.getStrengths(), String.class);
                    String highlight = strengths.isEmpty() ? "" : "，关键产出：" + strengths.get(0);
                    experienceLines.add(
                            "- 通过「%s」模拟任务（%s）：完成方案设计与结构化复盘，评估得分 %s/10%s"
                                    .formatted(task.getTitle(), task.getDifficulty(),
                                            e.getTotalScore().toPlainString(), highlight));
                });

        double rate = 0;
        long completed = submissionMapper.countDistinctCompletedTasks(userId);
        long published = taskService.listPublished().size();
        rate = published == 0 ? 0 : Math.round(completed * 1000.0 / published) / 10.0;

        String markdown = """
                # 简历素材草稿（基于 OfferDojo 练习记录自动生成）

                > 说明：以下内容来自你在 OfferDojo 的真实练习与 AI 评估记录，属于**练习口径**，
                > 请结合真实项目经历改写后再用于简历，不要直接复制。

                ## 技能自评（按评估维度）

                %s

                ## 实战练习经历（按评估得分排序）

                %s

                ## 练习数据

                - 完成模拟任务：%d / %d（%s%%）
                - 累计评估次数：%d
                """.formatted(
                String.join("\n", skillLines.isEmpty() ? List.of("- 暂无练习数据，先去任务中心完成第一题吧") : skillLines),
                String.join("\n", experienceLines.isEmpty() ? List.of("- 暂无完成任务记录") : experienceLines),
                completed, published, rate, evaluations.size());

        return Map.of("markdown", markdown, "completedTasks", completed, "publishedTasks", published);
    }

    /** JD 反向匹配：把岗位描述里的关键词与任务/八股的标签和标题做命中匹配 */
    public Map<String, Object> matchJd(String jdText) {
        String text = jdText == null ? "" : jdText.toLowerCase();

        List<Map<String, Object>> taskHits = new ArrayList<>();
        for (Task task : taskService.listPublished()) {
            List<String> matched = new ArrayList<>();
            // 标签是人工标注的关键词，命中即可信
            for (String tag : task.getTags() == null ? new String[0] : task.getTags().split(",")) {
                String t = tag.trim().toLowerCase();
                if (t.length() >= 2 && text.contains(t)) {
                    matched.add(tag.trim());
                }
            }
            // 标题里的「」关键词（题目强调的核心主题）
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("「(.+?)」")
                    .matcher(task.getTitle());
            while (m.find()) {
                String kw = m.group(1);
                if (kw.length() >= 2 && text.contains(kw.toLowerCase()) && !matched.contains(kw)) {
                    matched.add(kw);
                }
            }
            if (!matched.isEmpty()) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("task", taskService.toTaskVO(task, null));
                hit.put("score", (long) matched.size());
                hit.put("matched", matched);
                taskHits.add(hit);
            }
        }
        taskHits.sort((a, b) -> Long.compare((long) b.get("score"), (long) a.get("score")));

        List<Map<String, Object>> baguHits = new ArrayList<>();
        var rows = questionMapper.selectList(Wrappers.<com.jobsim.entity.InterviewQuestion>lambdaQuery()
                .eq(com.jobsim.entity.InterviewQuestion::getFrequency, "HIGH"));
        for (var q : rows) {
            List<String> matched = new ArrayList<>();
            for (String tag : (q.getTags() == null ? "" : q.getTags()).split(",")) {
                String t = tag.trim().toLowerCase();
                if (t.length() >= 2 && text.contains(t) && !matched.contains(tag.trim())) {
                    matched.add(tag.trim());
                }
            }
            if (!matched.isEmpty()) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("id", q.getId());
                hit.put("title", q.getTitle());
                hit.put("categoryLabel", com.jobsim.util.CategoryLabels.label(q.getCategory()));
                hit.put("score", (long) matched.size());
                hit.put("matched", matched);
                baguHits.add(hit);
            }
        }
        baguHits.sort((a, b) -> Long.compare((long) b.get("score"), (long) a.get("score")));

        return Map.of(
                "tasks", taskHits.stream().limit(8).toList(),
                "bagu", baguHits.stream().limit(8).toList(),
                "totalTaskHits", taskHits.size(),
                "totalBaguHits", baguHits.size());
    }
}
