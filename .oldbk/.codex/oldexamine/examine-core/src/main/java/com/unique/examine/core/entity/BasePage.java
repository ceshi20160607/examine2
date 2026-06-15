package com.unique.examine.core.entity;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 兼容生成器代码的分页返回（仅最小字段）。
 */
public class BasePage<T> extends Page<T> {
    public BasePage() {
        super();
    }

    public BasePage(long current, long size) {
        super(current, size);
    }
}

