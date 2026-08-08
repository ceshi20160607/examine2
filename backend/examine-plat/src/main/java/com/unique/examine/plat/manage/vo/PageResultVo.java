package com.unique.examine.plat.manage.vo;

import java.util.List;

public record PageResultVo<T>(
        List<T> items,
        long page,
        long size,
        long total
) {
    public PageResultVo {
        items = List.copyOf(items);
    }
}
