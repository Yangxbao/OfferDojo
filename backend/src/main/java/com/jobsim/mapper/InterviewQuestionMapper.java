package com.jobsim.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobsim.entity.InterviewQuestion;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {

    /** 按类目统计题数（复习区页签展示用） */
    @Select("SELECT category, COUNT(*) AS total FROM interview_question GROUP BY category")
    List<CategoryCountRow> selectCountByCategory();

    record CategoryCountRow(String category, Long total) {
    }
}
