package com.jobsim.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页参数收敛：所有列表接口的分页参数都经过这里，
 * 统一默认值与上限，防止恶意大分页把数据库拖垮。
 */
public class PageUtils {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    private PageUtils() {
    }

    public static <T> Page<T> of(Long page, Long size) {
        long p = page == null ? 1 : Math.max(page, 1);
        long s = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        return new Page<>(p, s);
    }
}
