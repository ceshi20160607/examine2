package com.unique.examine.core.entity;

import lombok.Data;

/**
 * 兼容生成器代码的分页入参（仅最小字段）。
 */
@Data
public class PageEntity {
    private long page = 1;
    private long limit = 20;
    private int pageType = 0;

    public <T> BasePage<T> parse() {
        return new BasePage<>(page, limit);
    }
}

