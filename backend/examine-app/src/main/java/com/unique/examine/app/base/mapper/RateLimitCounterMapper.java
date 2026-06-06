package com.unique.examine.app.base.mapper;

import com.unique.examine.app.base.entity.RateLimitCounter;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 限流窗口计数。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface RateLimitCounterMapper extends BaseMapper<RateLimitCounter> {
}
