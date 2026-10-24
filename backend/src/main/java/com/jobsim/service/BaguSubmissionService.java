package com.jobsim.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobsim.common.BusinessException;
import com.jobsim.common.ErrorCode;
import com.jobsim.common.PageResult;
import com.jobsim.entity.BaguSubmission;
import com.jobsim.entity.User;
import com.jobsim.mapper.BaguSubmissionMapper;
import com.jobsim.mapper.UserMapper;
import com.jobsim.util.CategoryLabels;
import com.jobsim.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 社区面试题投稿：草稿→提交审核→通过/驳回→撤回。
 * 未审核通过的内容绝不进入公共题库；作者展示遵循匿名选择；
 * 授权记录：用户必须显式选择 REVIEW_ONLY（仅审核）或 PUBLIC_DISPLAY（公开展示）。
 */
@Service
@RequiredArgsConstructor
public class BaguSubmissionService {

    private final BaguSubmissionMapper submissionMapper;
    private final UserMapper userMapper;

    public static final java.util.List<String> QUESTION_TYPES = java.util.List.of(
            "BAGU", "INTERVIEW", "PROJECT", "SYSTEM_DESIGN", "DEBUG");

    // ==================== 用户侧 ====================

    public PageResult<Map<String, Object>> mySubmissions(Long userId, Long page, Long size) {
        IPage<BaguSubmission> result = submissionMapper.selectPage(PageUtils.of(page, size),
                Wrappers.<BaguSubmission>lambdaQuery()
                        .eq(BaguSubmission::getUserId, userId)
                        .orderByDesc(BaguSubmission::getUpdatedAt));
        return PageResult.map(result, this::toOwnerVO);
    }

    public BaguSubmission create(Long userId, BaguSubmission input) {
        validate(input);
        BaguSubmission submission = new BaguSubmission();
        applyInput(submission, input);
        submission.setUserId(userId);
        submission.setStatus(BaguSubmission.STATUS_DRAFT);
        submissionMapper.insert(submission);
        return submission;
    }

    public BaguSubmission update(Long userId, Long id, BaguSubmission input) {
        BaguSubmission submission = requireOwned(userId, id);
        if (BaguSubmission.STATUS_PENDING.equals(submission.getStatus())
                || BaguSubmission.STATUS_APPROVED.equals(submission.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可编辑");
        }
        validate(input);
        applyInput(submission, input);
        submissionMapper.updateById(submission);
        return submission;
    }

    public BaguSubmission submit(Long userId, Long id) {
        BaguSubmission submission = requireOwned(userId, id);
        if (!BaguSubmission.STATUS_DRAFT.equals(submission.getStatus())
                && !BaguSubmission.STATUS_REJECTED.equals(submission.getStatus())
                && !BaguSubmission.STATUS_WITHDRAWN.equals(submission.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅草稿/被驳回/已撤回状态可提交");
        }
        // 提交前必须完成授权选择（仅审核 or 公开展示）
        if (!BaguSubmission.GRANT_REVIEW_ONLY.equals(submission.getLicenseGrant())
                && !BaguSubmission.GRANT_PUBLIC_DISPLAY.equals(submission.getLicenseGrant())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提交前需选择授权方式（仅提交审核 / 授权公开展示）");
        }
        submission.setStatus(BaguSubmission.STATUS_PENDING);
        submission.setLicenseGrantedAt(java.time.LocalDateTime.now());
        submissionMapper.updateById(submission);
        return submission;
    }

    public BaguSubmission withdraw(Long userId, Long id) {
        BaguSubmission submission = requireOwned(userId, id);
        if (BaguSubmission.STATUS_APPROVED.equals(submission.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布内容请联系管理员处理");
        }
        submission.setStatus(BaguSubmission.STATUS_WITHDRAWN);
        submissionMapper.updateById(submission);
        return submission;
    }

    public void delete(Long userId, Long id) {
        BaguSubmission submission = requireOwned(userId, id);
        if (BaguSubmission.STATUS_APPROVED.equals(submission.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布内容不能删除");
        }
        submissionMapper.deleteById(submission.getId());
    }

    // ==================== 公共题库（仅 APPROVED 且授权公开展示） ====================

    public PageResult<Map<String, Object>> publicPage(String category, String questionType,
                                                      String keyword, Long page, Long size) {
        IPage<BaguSubmission> result = submissionMapper.selectPage(PageUtils.of(page, size),
                Wrappers.<BaguSubmission>lambdaQuery()
                        .eq(BaguSubmission::getStatus, BaguSubmission.STATUS_APPROVED)
                        .eq(BaguSubmission::getLicenseGrant, BaguSubmission.GRANT_PUBLIC_DISPLAY)
                        .eq(StringUtils.hasText(category), BaguSubmission::getCategory, category)
                        .eq(StringUtils.hasText(questionType), BaguSubmission::getQuestionType, questionType)
                        .and(StringUtils.hasText(keyword),
                                w -> w.like(BaguSubmission::getTitle, keyword)
                                        .or().like(BaguSubmission::getContent, keyword))
                        .orderByDesc(BaguSubmission::getReviewedAt));
        return PageResult.map(result, this::toPublicVO);
    }

    public Map<String, Object> publicDetail(Long id) {
        BaguSubmission submission = submissionMapper.selectById(id);
        if (submission == null || !BaguSubmission.STATUS_APPROVED.equals(submission.getStatus())
                || !BaguSubmission.GRANT_PUBLIC_DISPLAY.equals(submission.getLicenseGrant())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在或未公开");
        }
        return toPublicVO(submission);
    }

    // ==================== 管理端 ====================

    public PageResult<Map<String, Object>> adminPage(String status, Long page, Long size) {
        IPage<BaguSubmission> result = submissionMapper.selectPage(PageUtils.of(page, size),
                Wrappers.<BaguSubmission>lambdaQuery()
                        .eq(StringUtils.hasText(status), BaguSubmission::getStatus, status)
                        .orderByDesc(BaguSubmission::getUpdatedAt));
        return PageResult.map(result, this::toAdminVO);
    }

    public BaguSubmission review(Long id, boolean approve, String comment) {
        BaguSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "投稿不存在");
        }
        if (!BaguSubmission.STATUS_PENDING.equals(submission.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审核状态可审核");
        }
        // 只有权公开展示的内容可通过并进入公共题库；仅审核的通过后仅投稿者可见
        if (approve && !BaguSubmission.GRANT_PUBLIC_DISPLAY.equals(submission.getLicenseGrant())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该投稿仅授权审核，未授权公开展示，不能发布到公共题库");
        }
        submission.setStatus(approve ? BaguSubmission.STATUS_APPROVED : BaguSubmission.STATUS_REJECTED);
        submission.setReviewComment(comment);
        submission.setReviewedAt(java.time.LocalDateTime.now());
        submissionMapper.updateById(submission);
        return submission;
    }

    /** 管理员下架已发布投稿 */
    public BaguSubmission offline(Long id, String reason) {
        BaguSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "投稿不存在");
        }
        submission.setStatus(BaguSubmission.STATUS_WITHDRAWN);
        submission.setReviewComment("管理员下架：" + (reason == null ? "" : reason));
        submissionMapper.updateById(submission);
        return submission;
    }

    // ==================== 私有 ====================

    private BaguSubmission requireOwned(Long userId, Long id) {
        BaguSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "投稿不存在");
        }
        if (!submission.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己的投稿");
        }
        return submission;
    }

    private void validate(BaguSubmission input) {
        if (!StringUtils.hasText(input.getTitle()) || input.getTitle().length() > 300) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题必填且不超过 300 字");
        }
        if (!StringUtils.hasText(input.getContent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "题目内容必填");
        }
        if (input.getContent().length() > 20000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "题目内容最长 20000 字");
        }
        if (!StringUtils.hasText(input.getCategory())
                || !CategoryLabels.LABELS.containsKey(input.getCategory())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类不合法（须与官方题库同一套类目）");
        }
        if (!QUESTION_TYPES.contains(input.getQuestionType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "题目类型不合法");
        }
        if (input.getReferenceUrl() != null && !input.getReferenceUrl().isBlank()
                && !input.getReferenceUrl().startsWith("http")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参考链接必须以 http 开头");
        }
    }

    private void applyInput(BaguSubmission s, BaguSubmission input) {
        s.setCategory(input.getCategory());
        s.setQuestionType(input.getQuestionType());
        s.setDifficulty(input.getDifficulty() == null ? "MEDIUM" : input.getDifficulty());
        s.setTitle(input.getTitle());
        s.setContent(input.getContent());
        s.setMyAnswer(input.getMyAnswer());
        s.setReferenceAnswer(input.getReferenceAnswer());
        s.setExample(input.getExample());
        s.setTags(input.getTags());
        s.setCompanyInfo(input.getCompanyInfo());
        s.setReferenceUrl(input.getReferenceUrl());
        s.setAnonymous(Boolean.TRUE.equals(input.getAnonymous()));
        s.setOriginType(input.getOriginType() == null ? "USER_ORIGINAL" : input.getOriginType());
        if (BaguSubmission.GRANT_REVIEW_ONLY.equals(input.getLicenseGrant())
                || BaguSubmission.GRANT_PUBLIC_DISPLAY.equals(input.getLicenseGrant())) {
            s.setLicenseGrant(input.getLicenseGrant());
        }
    }

    /** 投稿者视角：完整字段 */
    private Map<String, Object> toOwnerVO(BaguSubmission s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("category", s.getCategory());
        m.put("categoryLabel", CategoryLabels.label(s.getCategory()));
        m.put("questionType", s.getQuestionType());
        m.put("difficulty", s.getDifficulty());
        m.put("title", s.getTitle());
        m.put("content", s.getContent());
        m.put("myAnswer", s.getMyAnswer());
        m.put("referenceAnswer", s.getReferenceAnswer());
        m.put("example", s.getExample());
        m.put("tags", s.getTags());
        m.put("companyInfo", s.getCompanyInfo());
        m.put("referenceUrl", s.getReferenceUrl());
        m.put("anonymous", s.getAnonymous());
        m.put("originType", s.getOriginType());
        m.put("licenseGrant", s.getLicenseGrant());
        m.put("status", s.getStatus());
        m.put("reviewComment", s.getReviewComment());
        m.put("reviewedAt", s.getReviewedAt());
        m.put("updatedAt", s.getUpdatedAt());
        return m;
    }

    /** 公共题库视角：遵循匿名选择，不含审核内部字段 */
    private Map<String, Object> toPublicVO(BaguSubmission s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("category", s.getCategory());
        m.put("categoryLabel", CategoryLabels.label(s.getCategory()));
        m.put("questionType", s.getQuestionType());
        m.put("difficulty", s.getDifficulty());
        m.put("title", s.getTitle());
        m.put("content", s.getContent());
        m.put("referenceAnswer", s.getReferenceAnswer());
        m.put("example", s.getExample());
        m.put("tags", s.getTags());
        m.put("companyInfo", s.getCompanyInfo());
        m.put("referenceUrl", s.getReferenceUrl());
        // 匿名选择由投稿者控制；作者名仅在非匿名时展示
        if (!Boolean.TRUE.equals(s.getAnonymous())) {
            User author = userMapper.selectById(s.getUserId());
            m.put("authorName", author == null ? "未知用户" : author.getNickname());
        } else {
            m.put("authorName", "匿名同学");
        }
        m.put("originType", s.getOriginType());
        m.put("originLabel", "USER_ORIGINAL".equals(s.getOriginType()) ? "用户原创投稿" : "用户复盘整理");
        m.put("reviewedAt", s.getReviewedAt());
        return m;
    }

    /** 管理端视角：含作者信息便于审核 */
    private Map<String, Object> toAdminVO(BaguSubmission s) {
        Map<String, Object> m = toOwnerVO(s);
        User author = userMapper.selectById(s.getUserId());
        m.put("authorUsername", author == null ? "?" : author.getUsername());
        return m;
    }
}
