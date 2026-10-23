package com.jobsim.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * JSON 工具：项目中"列表类内容"（需求条目、评分维度等）以 JSON 字符串存库，
 * 读写集中在这里，方便统一排查格式问题。
 */
@Slf4j
public class JsonUtils {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // 数据库里的 JSON 字段可能随版本演进新增字段，反序列化时忽略未知字段，保证向前兼容
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private JsonUtils() {
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /** 把 JSON 数组字符串还原为 List；空串或坏数据返回空列表而不是抛异常，避免打崩页面 */
    public static <T> List<T> readList(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            log.warn("JSON 数组解析失败，返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    public static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    /** 容错版反序列化：失败返回 null（用于可选的提示数据等非关键字段） */
    public static <T> T fromJsonQuietly(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.warn("JSON 反序列化失败，按空处理: {}", e.getMessage());
            return null;
        }
    }
}
