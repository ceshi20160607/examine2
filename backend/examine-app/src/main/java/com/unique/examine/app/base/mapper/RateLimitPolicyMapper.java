package com.unique.examine.app.base.mapper;

import com.unique.examine.app.base.entity.RateLimitPolicy;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户端限流策略。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface RateLimitPolicyMapper extends BaseMapper<RateLimitPolicy> {
}
