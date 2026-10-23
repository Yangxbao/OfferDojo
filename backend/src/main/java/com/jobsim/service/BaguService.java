package com.jobsim.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobsim.common.PageResult;
import com.jobsim.dto.bagu.BaguVO;
import com.jobsim.entity.InterviewQuestion;
import com.jobsim.mapper.InterviewQuestionMapper;
import com.jobsim.util.CategoryLabels;
import com.jobsim.util.JsonUtils;
import com.jobsim.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 八股复习服务：分页检索（类目/频率/关键词）与类目统计。
 * 内容为只读题库，不做任何写操作（题目由 JSON 题库文件播种）。
 */
@Service
@RequiredArgsConstructor
public class BaguService {

    private final InterviewQuestionMapper questionMapper;

    public PageResult<BaguVO> page(String category, String frequency, String keyword, Long page, Long size) {
        var wrapper = Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(StringUtils.hasText(category), InterviewQuestion::getCategory, category)
                .eq(StringUtils.hasText(frequency), InterviewQuestion::getFrequency, frequency)
                .and(StringUtils.hasText(keyword),
                        w -> w.like(InterviewQuestion::getTitle, keyword)
                                .or().like(InterviewQuestion::getOneLiner, keyword)
                                .or().like(InterviewQuestion::getTags, keyword))
                .orderByAsc(InterviewQuestion::getCategory)
                .orderByDesc(InterviewQuestion::getFrequency)
                .orderByAsc(InterviewQuestion::getId);
        var result = questionMapper.selectPage(PageUtils.of(page, size), wrapper);
        return PageResult.map(result, this::toVO);
    }

    /** 类目统计（含中文标签），按题数降序 */
    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : questionMapper.selectCountByCategory()) {
            Map<String, Object> item = new HashMap<>();
            item.put("category", row.category());
            item.put("label", CategoryLabels.label(row.category()));
            item.put("count", row.total());
            result.add(item);
        }
        result.sort(Comparator.comparing(m -> -((Long) m.get("count"))));
        return result;
    }

    private BaguVO toVO(InterviewQuestion q) {
        return new BaguVO(
                q.getId(),
                q.getCode(),
                q.getCategory(),
                CategoryLabels.label(q.getCategory()),
                q.getTitle(),
                q.getFrequency(),
                q.getDifficulty(),
                q.getTags() == null ? List.of() : List.of(q.getTags().split(",")),
                q.getOneLiner(),
                q.getPlain(),
                JsonUtils.readList(q.getPoints(), String.class),
                q.getExample(),
                JsonUtils.readList(q.getFollowUps(), String.class));
    }
}
