package com.jobsim.util;

import java.util.Map;

/**
 * 类目编码与中文名的映射。
 * 存库只存编码（稳定、可筛选），中文标签由后端/前端映射展示。
 */
public class CategoryLabels {

    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("api-design", "需求分析与 API 设计"),
            Map.entry("db-design", "数据库设计"),
            Map.entry("troubleshooting", "问题排查"),
            Map.entry("testing", "测试与质量"),
            Map.entry("refactoring", "代码设计与重构"),
            Map.entry("java-core", "Java 核心与并发"),
            Map.entry("framework", "Spring 与框架"),
            Map.entry("cache", "Redis 与缓存"),
            Map.entry("network-os", "网络与操作系统"),
            Map.entry("mq", "消息队列与异步"),
            Map.entry("distributed", "分布式与微服务"),
            Map.entry("system-design", "系统设计场景"),
            Map.entry("engineering", "工程素养与工具"),
            Map.entry("ai-app", "AI 应用与 Agent")
    );

    private CategoryLabels() {
    }

    public static String label(String code) {
        return LABELS.getOrDefault(code, code);
    }
}
