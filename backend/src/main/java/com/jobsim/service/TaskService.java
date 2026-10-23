package com.jobsim.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobsim.common.BusinessException;
import com.jobsim.common.ErrorCode;
import com.jobsim.common.PageResult;
import com.jobsim.dto.admin.AdminTaskVO;
import com.jobsim.dto.task.RubricItem;
import com.jobsim.dto.task.TaskDetailVO;
import com.jobsim.dto.task.TaskSaveRequest;
import com.jobsim.dto.task.TaskVO;
import com.jobsim.entity.Submission;
import com.jobsim.entity.Task;
import com.jobsim.mapper.EvaluationMapper;
import com.jobsim.mapper.SubmissionMapper;
import com.jobsim.mapper.TaskMapper;
import com.jobsim.util.CategoryLabels;
import com.jobsim.util.JsonUtils;
import com.jobsim.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务服务：用户侧的浏览与筛选、管理端的增删改查（含下架）。
 * 同时沉淀了"任务 → VO 转换 + 用户练习状态"的公共逻辑，供工作台/评估复用。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String STATUS_DRAFT = "DRAFT";

    private final TaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;
    private final EvaluationMapper evaluationMapper;

    // ==================== 用户侧 ====================

    public PageResult<TaskVO> pageForUser(Long userId, String category, String difficulty,
                                          String keyword, Long page, Long size) {
        var wrapper = Wrappers.<Task>lambdaQuery()
                .eq(Task::getStatus, STATUS_PUBLISHED)
                .eq(StringUtils.hasText(category), Task::getCategory, category)
                .eq(StringUtils.hasText(difficulty), Task::getDifficulty, difficulty)
                .like(StringUtils.hasText(keyword), Task::getTitle, keyword)
                .orderByAsc(Task::getSortOrder)
                .orderByAsc(Task::getId);
        IPage<Task> result = taskMapper.selectPage(PageUtils.of(page, size), wrapper);
        return PageResult.map(result, t -> toTaskVO(t, userId));
    }

    public TaskDetailVO detailForUser(Long userId, Long taskId) {
        Task task = requirePublished(taskId);
        var status = myStatusMap(userId, List.of(task.getId()));
        var scores = bestScoreMap(userId, List.of(task.getId()));
        Submission draft = latestDraft(userId, taskId);
        Long latestSubmissionId = latestEvaluatedSubmissionId(userId, taskId);
        return new TaskDetailVO(
                task.getId(), task.getCode(), task.getTitle(), task.getCategory(),
                CategoryLabels.label(task.getCategory()), task.getDifficulty(),
                parseTags(task.getTags()), task.getSummary(), task.getEstimatedMinutes(),
                task.getStatus(),
                status.getOrDefault(task.getId(), "NOT_STARTED"),
                scores.get(task.getId()),
                task.getBackground(),
                JsonUtils.readList(task.getRequirements(), String.class),
                JsonUtils.readList(task.getConstraintItems(), String.class),
                JsonUtils.readList(task.getDeliverables(), String.class),
                JsonUtils.readList(task.getAcceptance(), String.class),
                JsonUtils.readList(task.getRubricJson(), RubricItem.class),
                draft == null ? null : draft.getId(),
                draft == null ? null : draft.getContent(),
                latestSubmissionId);
    }

    // ==================== 管理端 ====================

    public PageResult<AdminTaskVO> adminPage(String category, String difficulty, String status,
                                             String keyword, Long page, Long size) {
        var wrapper = Wrappers.<Task>lambdaQuery()
                .eq(StringUtils.hasText(category), Task::getCategory, category)
                .eq(StringUtils.hasText(difficulty), Task::getDifficulty, difficulty)
                .eq(StringUtils.hasText(status), Task::getStatus, status)
                .like(StringUtils.hasText(keyword), Task::getTitle, keyword)
                .orderByAsc(Task::getSortOrder)
                .orderByAsc(Task::getId);
        IPage<Task> result = taskMapper.selectPage(PageUtils.of(page, size), wrapper);
        Map<Long, TaskMapper.TaskStatsRow> stats = statsMap(result.getRecords());
        return PageResult.map(result, t -> toAdminTaskVO(t, stats.get(t.getId())));
    }

    public Task create(TaskSaveRequest req) {
        ensureCodeUnique(req.getCode());
        Task task = new Task();
        applyRequest(task, req);
        task.setStatus(req.getStatus() == null ? STATUS_PUBLISHED : req.getStatus());
        task.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        // 管理端创建的任务没有 Mock 提示数据，MockAiProvider 会走通用评分路径
        task.setMockHintsJson(null);
        taskMapper.insert(task);
        return task;
    }

    public Task update(Long id, TaskSaveRequest req) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        if (!task.getCode().equals(req.getCode())) {
            ensureCodeUnique(req.getCode());
        }
        applyRequest(task, req);
        if (StringUtils.hasText(req.getStatus())) {
            task.setStatus(req.getStatus());
        }
        // 不修改 mockHintsJson（updateById 默认忽略 null 字段，种子任务的提示数据得以保留）
        taskMapper.updateById(task);
        return task;
    }

    public void updateStatus(Long id, String status) {
        if (!STATUS_PUBLISHED.equals(status) && !STATUS_OFFLINE.equals(status) && !STATUS_DRAFT.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态不合法");
        }
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        task.setStatus(status);
        taskMapper.updateById(task);
    }

    // ==================== 公共复用 ====================

    public Task requirePublished(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !STATUS_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在或已下架");
        }
        return task;
    }

    public List<Task> listPublished() {
        return taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getStatus, STATUS_PUBLISHED)
                .orderByAsc(Task::getSortOrder).orderByAsc(Task::getId));
    }

    public TaskVO toTaskVO(Task task, Long userId) {
        var status = myStatusMap(userId, List.of(task.getId()));
        var scores = bestScoreMap(userId, List.of(task.getId()));
        return new TaskVO(task.getId(), task.getCode(), task.getTitle(), task.getCategory(),
                CategoryLabels.label(task.getCategory()), task.getDifficulty(),
                parseTags(task.getTags()), task.getSummary(), task.getEstimatedMinutes(),
                task.getStatus(),
                status.getOrDefault(task.getId(), "NOT_STARTED"),
                scores.get(task.getId()));
    }

    /**
     * 用户的任务练习状态：已完成 > 草稿中 > 未开始。
     * 按更新时间倒序遍历提交记录，第一次出现的状态优先级最高。
     */
    public Map<Long, String> myStatusMap(Long userId, List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (var row : submissionMapper.selectByUserAndTaskIds(userId, taskIds)) {
            result.putIfAbsent(row.taskId(),
                    Submission.STATUS_EVALUATED.equals(row.status()) ? "COMPLETED" : "DRAFTING");
        }
        return result;
    }

    public Map<Long, Double> bestScoreMap(Long userId, List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> result = new HashMap<>();
        var rows = evaluationMapper.selectList(Wrappers.<com.jobsim.entity.Evaluation>lambdaQuery()
                .select(com.jobsim.entity.Evaluation::getTaskId, com.jobsim.entity.Evaluation::getTotalScore)
                .eq(com.jobsim.entity.Evaluation::getUserId, userId)
                .in(com.jobsim.entity.Evaluation::getTaskId, taskIds));
        for (var eval : rows) {
            if (eval.getTotalScore() != null) {
                result.merge(eval.getTaskId(), eval.getTotalScore().doubleValue(), Math::max);
            }
        }
        return result;
    }

    /**
     * 下一步推荐任务：优先返回评估中建议的任务编码（已发布、排除当前任务），
     * 不足时补充"用户未完成、难度从低到高"的任务，凑满 limit 个。
     */
    public List<TaskVO> recommendNext(Long userId, Long currentTaskId, List<String> preferredCodes, int limit) {
        List<Task> published = listPublished();
        Set<Long> doneIds = completedTaskIds(userId);
        List<TaskVO> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (preferredCodes != null) {
            for (String code : preferredCodes) {
                published.stream()
                        .filter(t -> t.getCode().equals(code) && !t.getId().equals(currentTaskId))
                        .findFirst()
                        .ifPresent(t -> {
                            if (seen.add(t.getId())) {
                                result.add(toTaskVO(t, userId));
                            }
                        });
            }
        }
        if (result.size() < limit) {
            List<Task> candidates = published.stream()
                    .filter(t -> !t.getId().equals(currentTaskId))
                    .filter(t -> !doneIds.contains(t.getId()))
                    .sorted(Comparator.comparingInt(TaskService::difficultyRank))
                    .toList();

            for (Task t : candidates) {
                if (result.size() >= limit) {
                    break;
                }
                if (seen.add(t.getId())) {
                    result.add(toTaskVO(t, userId));
                }
            }
        }
        return result;
    }

    public Set<Long> completedTaskIds(Long userId) {
        var rows = submissionMapper.selectList(Wrappers.<Submission>lambdaQuery()
                .select(Submission::getTaskId)
                .eq(Submission::getUserId, userId)
                .eq(Submission::getStatus, Submission.STATUS_EVALUATED));
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> ids.add(r.getTaskId()));
        return ids;
    }

    /** 难度排序权重：EASY < MEDIUM < HARD < EXPERT（推荐排序用） */
    public static int difficultyRank(Task t) {
        return switch (t.getDifficulty()) {
            case "EASY" -> 0;
            case "MEDIUM" -> 1;
            case "HARD" -> 2;
            default -> 3;
        };
    }

    // ==================== 私有工具 ====================

    private void applyRequest(Task task, TaskSaveRequest req) {
        task.setCode(req.getCode());
        task.setTitle(req.getTitle());
        task.setCategory(req.getCategory());
        task.setDifficulty(req.getDifficulty());
        task.setTags(req.getTags() == null ? "" : String.join(",", req.getTags()));
        task.setSummary(req.getSummary());
        task.setEstimatedMinutes(req.getEstimatedMinutes());
        task.setBackground(req.getBackground());
        task.setRequirements(JsonUtils.toJson(req.getRequirements()));
        task.setConstraintItems(JsonUtils.toJson(req.getConstraintItems()));
        task.setDeliverables(JsonUtils.toJson(req.getDeliverables()));
        task.setAcceptance(JsonUtils.toJson(req.getAcceptance()));
        task.setRubricJson(JsonUtils.toJson(req.getRubric()));
    }

    private void ensureCodeUnique(String code) {
        Long count = taskMapper.selectCount(Wrappers.<Task>lambdaQuery().eq(Task::getCode, code));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务编码已存在：" + code);
        }
    }

    private Map<Long, TaskMapper.TaskStatsRow> statsMap(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        Map<Long, TaskMapper.TaskStatsRow> map = new HashMap<>();
        for (var row : taskMapper.selectTaskStats(tasks.stream().map(Task::getId).toList())) {
            map.put(row.taskId(), row);
        }
        return map;
    }

    private AdminTaskVO toAdminTaskVO(Task t, TaskMapper.TaskStatsRow stats) {
        return new AdminTaskVO(t.getId(), t.getCode(), t.getTitle(), t.getCategory(),
                CategoryLabels.label(t.getCategory()), t.getDifficulty(), t.getTags(),
                t.getSummary(), t.getEstimatedMinutes(), t.getStatus(), t.getSortOrder(),
                t.getCreatedAt(), t.getUpdatedAt(),
                stats == null ? 0L : stats.attempts(),
                stats == null ? 0L : stats.userCount(),
                stats == null ? null : stats.avgScore());
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return List.of(tags.split(","));
    }

    private Submission latestDraft(Long userId, Long taskId) {
        return submissionMapper.selectOne(Wrappers.<Submission>lambdaQuery()
                .eq(Submission::getUserId, userId)
                .eq(Submission::getTaskId, taskId)
                .eq(Submission::getStatus, Submission.STATUS_DRAFT)
                .orderByDesc(Submission::getUpdatedAt)
                .last("LIMIT 1"));
    }

    private Long latestEvaluatedSubmissionId(Long userId, Long taskId) {
        Submission sub = submissionMapper.selectOne(Wrappers.<Submission>lambdaQuery()
                .select(Submission::getId)
                .eq(Submission::getUserId, userId)
                .eq(Submission::getTaskId, taskId)
                .eq(Submission::getStatus, Submission.STATUS_EVALUATED)
                .orderByDesc(Submission::getUpdatedAt)
                .last("LIMIT 1"));
        return sub == null ? null : sub.getId();
    }
}
