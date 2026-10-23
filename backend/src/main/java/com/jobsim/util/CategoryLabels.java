package com.jobsim.util;

import java.util.Map;

/**
 * 类目编码与中文名的映射。
 * 存库只存编码（稳定、可筛选），中文标签由后端/前端映射展示。
 */
public class CategoryLabels {

    public static final Map<String, String> LABELS = Map.of(
            "api-design", "需求分析与 API 设计",
            "db-design", "数据库设计",
            "troubleshooting", "问题排查",
            "testing", "测试与质量",
            "refactoring", "代码设计与重构"
    );

    private CategoryLabels() {
    }

    public static String label(String code) {
        return LABELS.getOrDefault(code, code);
    }
}
