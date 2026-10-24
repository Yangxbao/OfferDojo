package com.jobsim.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.common.BusinessException;
import com.jobsim.common.ErrorCode;
import com.jobsim.dto.learning.PlanItemVO;
import com.jobsim.entity.LearningNode;
import com.jobsim.entity.LearningPlan;
import com.jobsim.entity.LearningResource;
import com.jobsim.mapper.LearningNodeMapper;
import com.jobsim.mapper.LearningPlanMapper;
import com.jobsim.mapper.LearningResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 个人学习计划与记录：加入/移除/状态流转/心得记录，全部按当前用户过滤。
 * 状态语义：TODO 待学习 / IN_PROGRESS 学习中 / DONE 已完成（用户标记；
 * 节点关联任务的真实验收状态另由任务题库与评估记录给出，两者在页面上并行展示）。
 */
@Service
@RequiredArgsConstructor
public class LearningPlanService {

    private final LearningPlanMapper planMapper;
    private final LearningResourceMapper resourceMapper;
    private final LearningNodeMapper nodeMapper;

    /** 我的计划列表（按更新时间倒序） */
    public List<PlanItemVO> myPlan(Long userId, String status) {
        return planMapper.selectList(Wrappers.<LearningPlan>lambdaQuery()
                        .eq(LearningPlan::getUserId, userId)
                        .eq(status != null && !status.isBlank(), LearningPlan::getStatus, status)
                        .orderByDesc(LearningPlan::getUpdatedAt))
                .stream().map(this::toVO).toList();
    }

    /** 加入计划（幂等：已存在则原样返回），加入即标记待学习 */
    public PlanItemVO add(Long userId, String itemType, String itemCode) {
        validateItem(itemType, itemCode);
        LearningPlan existing = planMapper.selectOne(Wrappers.<LearningPlan>lambdaQuery()
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getItemType, itemType)
                .eq(LearningPlan::getItemCode, itemCode));
        if (existing != null) {
            return toVO(existing);
        }
        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        plan.setItemType(itemType);
        plan.setItemCode(itemCode);
        plan.setStatus(LearningPlan.STATUS_TODO);
        plan.setStartedAt(LocalDateTime.now());
        planMapper.insert(plan);
        return toVO(plan);
    }

    /** 更新状态与心得：状态流转 TODO → IN_PROGRESS → DONE（允许回退），完成时记录完成时间 */
    public PlanItemVO updateStatus(Long userId, Long planId, String status, String note) {
        LearningPlan plan = requireOwned(userId, planId);
        if (!LearningPlan.STATUS_TODO.equals(status) && !LearningPlan.STATUS_IN_PROGRESS.equals(status)
                && !LearningPlan.STATUS_DONE.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态不合法：TODO / IN_PROGRESS / DONE");
        }
        plan.setStatus(status);
        if (note != null) {
            plan.setNote(note);
        }
        if (LearningPlan.STATUS_IN_PROGRESS.equals(status) && plan.getStartedAt() == null) {
            plan.setStartedAt(LocalDateTime.now());
        }
        plan.setCompletedAt(LearningPlan.STATUS_DONE.equals(status) ? LocalDateTime.now() : null);
        planMapper.updateById(plan);
        return toVO(plan);
    }

    /** 移出计划 */
    public void remove(Long userId, Long planId) {
        LearningPlan plan = requireOwned(userId, planId);
        planMapper.deleteById(plan.getId());
    }

    private LearningPlan requireOwned(Long userId, Long planId) {
        LearningPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习计划条目不存在");
        }
        // 归属校验：不能查看或修改他人的学习记录
        if (!plan.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己的学习计划");
        }
        return plan;
    }

    /** 校验条目真实存在（资源需非下架，节点需已配置） */
    private void validateItem(String itemType, String itemCode) {
        switch (itemType) {
            case "RESOURCE" -> {
                LearningResource r = resourceMapper.selectOne(Wrappers.<LearningResource>lambdaQuery()
                        .eq(LearningResource::getCode, itemCode));
                if (r == null || LearningResource.STATUS_OFFLINE.equals(r.getVerifiedStatus())) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在或已下架，无法加入计划");
                }
            }
            case "NODE" -> {
                Long count = nodeMapper.selectCount(Wrappers.<LearningNode>lambdaQuery()
                        .eq(LearningNode::getCode, itemCode));
                if (count == null || count == 0) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "学习节点不存在，无法加入计划");
                }
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "条目类型不合法：RESOURCE / NODE");
        }
    }

    private PlanItemVO toVO(LearningPlan p) {
        String title = "";
        if ("RESOURCE".equals(p.getItemType())) {
            LearningResource r = resourceMapper.selectOne(Wrappers.<LearningResource>lambdaQuery()
                    .select(LearningResource::getTitle)
                    .eq(LearningResource::getCode, p.getItemCode()));
            title = r == null ? p.getItemCode() : r.getTitle();
        } else {
            LearningNode n = nodeMapper.selectOne(Wrappers.<LearningNode>lambdaQuery()
                    .select(LearningNode::getTitle)
                    .eq(LearningNode::getCode, p.getItemCode()));
            title = n == null ? p.getItemCode() : n.getTitle();
        }
        return new PlanItemVO(p.getId(), p.getItemType(), p.getItemCode(), title,
                p.getStatus(), p.getNote(), p.getStartedAt(), p.getCompletedAt(), p.getUpdatedAt());
    }
}
