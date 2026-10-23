package com.jobsim.common;

import lombok.Getter;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果（前后端约定的分页结构）。
 * 从 MyBatis-Plus 的 IPage 转换而来，避免把 ORM 的分页对象直接暴露给前端。
 */
@Getter
public class PageResult<T> {

    private final List<T> list;
    private final long total;
    private final long page;
    private final long size;

    public PageResult(List<T> list, long total, long page, long size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /** 从 IPage（MyBatis-Plus 分页结果）构建，元素原样透传 */
    public static <T> PageResult<T> from(com.baomidou.mybatisplus.core.metadata.IPage<T> p) {
        return new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 从 IPage 构建，并逐条转换为 VO（Page 泛型不变，转换在 map 中完成） */
    public static <E, T> PageResult<T> map(com.baomidou.mybatisplus.core.metadata.IPage<E> p, Function<E, T> converter) {
        List<T> list = p.getRecords().stream().map(converter).toList();
        return new PageResult<>(list, p.getTotal(), p.getCurrent(), p.getSize());
    }
}
